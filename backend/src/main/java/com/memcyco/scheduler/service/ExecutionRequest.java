package com.memcyco.scheduler.service;

import com.memcyco.scheduler.entity.TriggerType;

import java.util.UUID;

/** What a fired Quartz trigger asks to run. */
public record ExecutionRequest(UUID schedulingId, TriggerType triggerType, int attempt, UUID previousAttemptId) {
}
