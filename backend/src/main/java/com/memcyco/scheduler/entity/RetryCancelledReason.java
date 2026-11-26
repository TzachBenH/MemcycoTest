package com.memcyco.scheduler.entity;

/** Why a pending retry was cancelled before it ran. */
public enum RetryCancelledReason {
    /** A new regular (or manual) run started, making the retry pointless. */
    SUPERSEDED,
    PAUSED,
    EDITED,
    DELETED
}
