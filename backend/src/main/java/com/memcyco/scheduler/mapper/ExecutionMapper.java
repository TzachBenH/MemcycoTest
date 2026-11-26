package com.memcyco.scheduler.mapper;

import com.memcyco.scheduler.dto.ExecutionResponse;
import com.memcyco.scheduler.entity.TaskExecution;
import com.memcyco.scheduler.service.RetryPolicy;

import java.time.Duration;

public final class ExecutionMapper {

    private ExecutionMapper() {
    }

    public static ExecutionResponse toResponse(TaskExecution e) {
        Long durationMs = e.getFinishedAt() == null ? null
                : Duration.between(e.getStartedAt(), e.getFinishedAt()).toMillis();
        return new ExecutionResponse(e.getId(), e.getStatus(), e.getTriggerType(), e.getAttempt(),
                RetryPolicy.MAX_ATTEMPTS, e.getPreviousAttemptId(), e.getNextRetryAt(),
                e.getRetryCancelledReason(), e.getExecutedBy(), e.getStartedAt(), e.getLastHeartbeatAt(),
                e.getFinishedAt(), durationMs, e.getErrorMessage(), e.getParameters());
    }
}
