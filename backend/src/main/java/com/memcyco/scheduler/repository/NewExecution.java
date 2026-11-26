package com.memcyco.scheduler.repository;

import com.memcyco.scheduler.entity.TriggerType;

import java.util.Map;
import java.util.UUID;

/** Everything needed to insert an execution attempt row. Timestamps come from the database clock. */
public record NewExecution(UUID id, UUID schedulingId, String taskKey, Map<String, Object> parameters,
                           String runKey, TriggerType triggerType, int attempt, UUID previousAttemptId,
                           String executedBy) {
}
