package com.memcyco.scheduler.entity;

/** What started an execution. */
public enum TriggerType {
    /** The scheduling's own timing fired. */
    SCHEDULED,
    /** "Run now" from the UI/API. */
    MANUAL,
    /** Automatic retry after a failed attempt. */
    RETRY
}
