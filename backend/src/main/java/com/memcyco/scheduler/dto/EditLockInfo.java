package com.memcyco.scheduler.dto;

import java.time.Instant;

/**
 * Someone currently holds the edit lock.
 *
 * @param lockedBy display name of the holder ("anonymous" until authentication exists)
 * @param mine     true when the requesting browser tab holds it
 */
public record EditLockInfo(String lockedBy, Instant since, Instant expiresAt, boolean mine) {
}
