package com.memcyco.scheduler.service;

import com.memcyco.scheduler.dto.EditLockInfo;
import com.memcyco.scheduler.dto.PageResponse;
import com.memcyco.scheduler.dto.SchedulingResponse;
import com.memcyco.scheduler.dto.SchedulingStatus;
import com.memcyco.scheduler.dto.WarningResponse;
import com.memcyco.scheduler.entity.Scheduling;
import com.memcyco.scheduler.entity.TaskExecution;
import com.memcyco.scheduler.exception.NotFoundException;
import com.memcyco.scheduler.mapper.SchedulingMapper;
import com.memcyco.scheduler.repository.SchedulingRepository;
import com.memcyco.scheduler.repository.SchedulingSpecifications;
import com.memcyco.scheduler.repository.TaskExecutionRepository;
import com.memcyco.scheduler.task.TaskRegistry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Read side: builds the responses for the schedulings table. A page is loaded with a fixed number of queries
 * (schedulings + timings, weekly days in batches, latest executions, active edit locks, next fire times) —
 * no per-row queries.
 */
@Service
@Transactional(readOnly = true)
public class SchedulingQueryService {

    private final SchedulingRepository schedulings;
    private final TaskExecutionRepository executions;
    private final EditLockService editLocks;
    private final TaskRegistry taskRegistry;
    private final QuartzSchedulingGateway quartz;
    private final Clock clock;

    public SchedulingQueryService(SchedulingRepository schedulings, TaskExecutionRepository executions,
                                  EditLockService editLocks, TaskRegistry taskRegistry,
                                  QuartzSchedulingGateway quartz, Clock clock) {
        this.schedulings = schedulings;
        this.executions = executions;
        this.editLocks = editLocks;
        this.taskRegistry = taskRegistry;
        this.quartz = quartz;
        this.clock = clock;
    }

    public PageResponse<SchedulingResponse> list(String search, String taskKey, SchedulingStatus status,
                                                 Pageable pageable, String editorId) {
        Instant now = clock.instant();
        Page<Scheduling> page = schedulings.findAll(
                SchedulingSpecifications.matching(search, taskKey, status, now), pageable);
        List<UUID> ids = page.getContent().stream().map(Scheduling::getId).toList();

        Map<UUID, TaskExecution> lastExecutions = ids.isEmpty() ? Map.of()
                : executions.findLatestBySchedulingIds(ids).stream()
                .collect(Collectors.toMap(TaskExecution::getSchedulingId, Function.identity()));
        Map<UUID, EditLockInfo> locks = editLocks.activeLocks(ids, editorId);
        Map<UUID, Instant> nextFireTimes = quartz.nextFireTimes(ids);

        return PageResponse.of(page, page.getContent().stream()
                .map(s -> SchedulingMapper.toResponse(s, taskRegistry.find(s.getTaskKey()),
                        lastExecutions.get(s.getId()), locks.get(s.getId()), List.of(),
                        nextFireTimes.get(s.getId()), now))
                .toList());
    }

    public SchedulingResponse get(UUID id, String editorId) {
        return toDetailedResponse(findOrThrow(id), editorId);
    }

    /** Full response for one scheduling, including duplicate warnings. */
    public SchedulingResponse toDetailedResponse(Scheduling s, String editorId) {
        TaskExecution last = executions.findLatestBySchedulingIds(List.of(s.getId())).stream()
                .findFirst().orElse(null);
        EditLockInfo lock = editLocks.activeLock(s.getId(), editorId).orElse(null);
        Instant nextFireTime = quartz.nextFireTimes(List.of(s.getId())).get(s.getId());
        return SchedulingMapper.toResponse(s, taskRegistry.find(s.getTaskKey()), last, lock, warnings(s),
                nextFireTime, clock.instant());
    }

    public Scheduling findOrThrow(UUID id) {
        return schedulings.findById(id).orElseThrow(() -> new NotFoundException("Scheduling", id));
    }

    /** Other schedulings with the same task, parameters and timing. */
    private List<WarningResponse> warnings(Scheduling s) {
        return schedulings.findByFingerprintAndIdNot(s.getFingerprint(), s.getId()).stream()
                .map(other -> other.isEnabled()
                        ? new WarningResponse(WarningResponse.ACTIVE_DUPLICATE_EXISTS,
                        "\"" + other.getName() + "\" is active with the same settings; "
                                + "this one cannot be resumed while it is active",
                        other.getId(), other.getName())
                        : new WarningResponse(WarningResponse.PAUSED_DUPLICATE_EXISTS,
                        "A paused scheduling with the same settings exists: \"" + other.getName() + "\"",
                        other.getId(), other.getName()))
                .toList();
    }
}
