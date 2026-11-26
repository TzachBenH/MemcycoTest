package com.memcyco.scheduler.service;

import com.memcyco.scheduler.dto.ExecutionResponse;
import com.memcyco.scheduler.dto.PageResponse;
import com.memcyco.scheduler.entity.ExecutionStatus;
import com.memcyco.scheduler.entity.TaskExecution;
import com.memcyco.scheduler.exception.NotFoundException;
import com.memcyco.scheduler.mapper.ExecutionMapper;
import com.memcyco.scheduler.repository.SchedulingRepository;
import com.memcyco.scheduler.repository.TaskExecutionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Execution history. (Recording executions is added with the execution engine.) */
@Service
@Transactional(readOnly = true)
public class ExecutionService {

    private final TaskExecutionRepository executions;
    private final SchedulingRepository schedulings;

    public ExecutionService(TaskExecutionRepository executions, SchedulingRepository schedulings) {
        this.executions = executions;
        this.schedulings = schedulings;
    }

    /** Newest first. */
    public PageResponse<ExecutionResponse> history(UUID schedulingId, ExecutionStatus status, int page, int size) {
        if (!schedulings.existsById(schedulingId)) {
            throw new NotFoundException("Scheduling", schedulingId);
        }
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "startedAt"));
        Page<TaskExecution> result = status == null
                ? executions.findBySchedulingId(schedulingId, pageable)
                : executions.findBySchedulingIdAndStatus(schedulingId, status, pageable);
        return PageResponse.of(result, ExecutionMapper::toResponse);
    }
}
