package com.memcyco.scheduler.entity;

/** The four supported timing types; stored in {@code schedule_timings.type} (JPA discriminator). */
public enum ScheduleType {
    ONE_TIME,
    INTERVAL,
    WEEKLY,
    CRON
}
