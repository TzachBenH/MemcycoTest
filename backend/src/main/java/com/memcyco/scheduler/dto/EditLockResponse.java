package com.memcyco.scheduler.dto;

import java.time.Instant;
import java.util.UUID;

/** Returned to the editor that holds the lock. The token must be sent with renew, release and save. */
public record EditLockResponse(UUID lockToken, Instant expiresAt) {
}
