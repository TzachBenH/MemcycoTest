package com.memcyco.scheduler.entity.timing;

import com.memcyco.scheduler.entity.IntervalUnit;
import com.memcyco.scheduler.entity.ScheduleType;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.Table;

import java.time.Duration;
import java.time.Instant;

/** Runs every {@code intervalValue} {@code intervalUnit}, starting at {@code startAt}. */
@Entity
@Table(name = "interval_timings")
@PrimaryKeyJoinColumn(name = "id")
@DiscriminatorValue("INTERVAL")
public class IntervalTiming extends ScheduleTiming {

    @Column(name = "interval_value", nullable = false)
    private int intervalValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "interval_unit", nullable = false, length = 10)
    private IntervalUnit intervalUnit;

    /** Defaults to the creation time when the user does not choose one; part of the scheduling's identity. */
    @Column(name = "start_at", nullable = false)
    private Instant startAt;

    protected IntervalTiming() {
        // for JPA
    }

    public IntervalTiming(int intervalValue, IntervalUnit intervalUnit, Instant startAt, String timezone) {
        super(timezone);
        this.intervalValue = intervalValue;
        this.intervalUnit = intervalUnit;
        this.startAt = startAt;
    }

    @Override
    public ScheduleType getType() {
        return ScheduleType.INTERVAL;
    }

    public int getIntervalValue() {
        return intervalValue;
    }

    public IntervalUnit getIntervalUnit() {
        return intervalUnit;
    }

    public Instant getStartAt() {
        return startAt;
    }

    public Duration interval() {
        return intervalUnit.toDuration(intervalValue);
    }
}
