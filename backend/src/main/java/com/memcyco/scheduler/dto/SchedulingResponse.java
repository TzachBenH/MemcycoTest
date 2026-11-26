package com.memcyco.scheduler.dto;

import com.memcyco.scheduler.dto.timing.TimingDto;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** One scheduling = one row in the UI table. */
public record SchedulingResponse(UUID id, String name, TaskSummary task, Map<String, Object> parameters,
                                 TimingDto timing, boolean enabled, SchedulingStatus status, Instant nextFireTime,
                                 LastExecutionResponse lastExecution, EditLockInfo editLock,
                                 List<WarningResponse> warnings, Long version, Instant createdAt,
                                 Instant updatedAt, String createdBy, String updatedBy) {
}
