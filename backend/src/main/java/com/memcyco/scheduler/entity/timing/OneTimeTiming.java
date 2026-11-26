package com.memcyco.scheduler.entity.timing;

import com.memcyco.scheduler.entity.ScheduleType;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.Table;

import java.time.Instant;

/** Runs exactly once, at {@code runAt}. */
@Entity
@Table(name = "one_time_timings")
@PrimaryKeyJoinColumn(name = "id")
@DiscriminatorValue("ONE_TIME")
public class OneTimeTiming extends ScheduleTiming {

    @Column(name = "run_at", nullable = false)
    private Instant runAt;

    protected OneTimeTiming() {
        // for JPA
    }

    public OneTimeTiming(Instant runAt, String timezone) {
        super(timezone);
        this.runAt = runAt;
    }

    @Override
    public ScheduleType getType() {
        return ScheduleType.ONE_TIME;
    }

    public Instant getRunAt() {
        return runAt;
    }
}
