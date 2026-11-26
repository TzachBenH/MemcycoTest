package com.memcyco.scheduler.dto;

import com.memcyco.scheduler.entity.ExecutionStatus;
import com.memcyco.scheduler.entity.RetryCancelledReason;
import com.memcyco.scheduler.entity.TriggerType;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** One execution attempt, for the history page. */
public record ExecutionResponse(UUID id, ExecutionStatus status, TriggerType triggerType, int attempt,
                                int maxAttempts, UUID previousAttemptId, Instant nextRetryAt,
                                RetryCancelledReason retryCancelledReason, String executedBy, Instant startedAt,
                                Instant lastHeartbeatAt, Instant finishedAt, Long durationMs, String errorMessage,
                                Map<String, Object> parameters) {
}
