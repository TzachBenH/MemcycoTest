package com.memcyco.scheduler.entity;

import java.time.Duration;

/** Unit of an interval timing ("every N minutes/hours"). */
public enum IntervalUnit {
    MINUTES(Duration.ofMinutes(1)),
    HOURS(Duration.ofHours(1));

    private final Duration unitDuration;

    IntervalUnit(Duration unitDuration) {
        this.unitDuration = unitDuration;
    }

    public Duration toDuration(int amount) {
        return unitDuration.multipliedBy(amount);
    }
}
