package com.memcyco.scheduler.mapper;

import com.memcyco.scheduler.dto.EditLockInfo;
import com.memcyco.scheduler.dto.LastExecutionResponse;
import com.memcyco.scheduler.dto.SchedulingResponse;
import com.memcyco.scheduler.dto.SchedulingStatus;
import com.memcyco.scheduler.dto.TaskSummary;
import com.memcyco.scheduler.dto.WarningResponse;
import com.memcyco.scheduler.entity.Scheduling;
import com.memcyco.scheduler.entity.TaskExecution;
import com.memcyco.scheduler.entity.timing.OneTimeTiming;
import com.memcyco.scheduler.service.RetryPolicy;
import com.memcyco.scheduler.task.Task;
import org.hibernate.Hibernate;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public final class SchedulingMapper {

    private SchedulingMapper() {
    }

    @SuppressWarnings("java:S107")
    public static SchedulingResponse toResponse(Scheduling s, Optional<Task> task, TaskExecution lastExecution,
                                                EditLockInfo editLock, List<WarningResponse> warnings,
                                                Instant nextFireTime, Instant now) {
        TaskSummary taskSummary = task
                .map(t -> new TaskSummary(t.key(), t.name(), true))
                .orElseGet(() -> new TaskSummary(s.getTaskKey(), s.getTaskKey(), false));
        return new SchedulingResponse(s.getId(), s.getName(), taskSummary, s.getParameters(),
                TimingMapper.toDto(s.getTiming()), s.isEnabled(), status(s, now), nextFireTime,
                lastExecution == null ? null : toLastExecution(lastExecution), editLock, warnings, s.getVersion(),
                s.getCreatedAt(), s.getUpdatedAt(), s.getCreatedBy(), s.getUpdatedBy());
    }

    /** PAUSED if disabled; COMPLETED if a one-time scheduling whose run time has passed; otherwise ACTIVE. */
    public static SchedulingStatus status(Scheduling s, Instant now) {
        if (!s.isEnabled()) {
            return SchedulingStatus.PAUSED;
        }
        if (Hibernate.unproxy(s.getTiming()) instanceof OneTimeTiming oneTime && !oneTime.getRunAt().isAfter(now)) {
            return SchedulingStatus.COMPLETED;
        }
        return SchedulingStatus.ACTIVE;
    }

    private static LastExecutionResponse toLastExecution(TaskExecution e) {
        return new LastExecutionResponse(e.getId(), e.getStatus(), e.getTriggerType(), e.getAttempt(),
                RetryPolicy.MAX_ATTEMPTS, e.getStartedAt(), e.getFinishedAt(), e.getNextRetryAt(),
                e.getRetryCancelledReason(), e.getErrorMessage());
    }
}
