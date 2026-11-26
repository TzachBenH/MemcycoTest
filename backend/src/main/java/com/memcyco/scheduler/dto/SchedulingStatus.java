package com.memcyco.scheduler.dto;

/** Derived (not stored) status of a scheduling. */
public enum SchedulingStatus {
    /** Enabled and will run again. */
    ACTIVE,
    /** Disabled by the user. */
    PAUSED,
    /** A one-time scheduling whose run time has passed. */
    COMPLETED
}
