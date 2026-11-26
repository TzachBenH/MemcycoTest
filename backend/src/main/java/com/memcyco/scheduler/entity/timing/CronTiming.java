package com.memcyco.scheduler.entity.timing;

import com.memcyco.scheduler.entity.ScheduleType;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.Table;

/** Runs according to a Quartz cron expression (6–7 fields, including seconds). */
@Entity
@Table(name = "cron_timings")
@PrimaryKeyJoinColumn(name = "id")
@DiscriminatorValue("CRON")
public class CronTiming extends ScheduleTiming {

    @Column(name = "cron_expression", nullable = false, length = 120)
    private String cronExpression;

    protected CronTiming() {
        // for JPA
    }

    public CronTiming(String cronExpression, String timezone) {
        super(timezone);
        this.cronExpression = cronExpression;
    }

    @Override
    public ScheduleType getType() {
        return ScheduleType.CRON;
    }

    public String getCronExpression() {
        return cronExpression;
    }
}
