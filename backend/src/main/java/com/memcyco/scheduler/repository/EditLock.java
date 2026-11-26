package com.memcyco.scheduler.repository;

import java.time.Instant;
import java.util.UUID;

/** A row of {@code scheduling_edit_locks}. {@code lockedBy} is "<user>:<editor tab id>". */
public record EditLock(UUID schedulingId, UUID lockToken, String lockedBy, Instant acquiredAt, Instant expiresAt) {
}
