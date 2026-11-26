package com.memcyco.scheduler.dto;

import com.memcyco.scheduler.entity.ExecutionStatus;
import com.memcyco.scheduler.entity.RetryCancelledReason;
import com.memcyco.scheduler.entity.TriggerType;

import java.time.Instant;
import java.util.UUID;

/**
 * Summary of the most recent execution attempt, shown in the table's "Last run" column.
 * A retry is pending when {@code nextRetryAt} is set and {@code retryCancelledReason} is not.
 */
public record LastExecutionResponse(UUID id, ExecutionStatus status, TriggerType triggerType, int attempt,
                                    int maxAttempts, Instant startedAt, Instant finishedAt, Instant nextRetryAt,
                                    RetryCancelledReason retryCancelledReason, String errorMessage) {
}
