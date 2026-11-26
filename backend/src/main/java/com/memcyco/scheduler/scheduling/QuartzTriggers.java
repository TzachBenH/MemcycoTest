package com.memcyco.scheduler.scheduling;

import org.quartz.CronExpression;
import org.quartz.CronScheduleBuilder;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;

import java.text.ParseException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Date;
import java.util.Optional;
import java.util.TimeZone;

/** Trigger-building helpers shared by the timing strategies. */
final class QuartzTriggers {

    private QuartzTriggers() {
    }

    /**
     * A cron trigger in the given zone. Missed runs (system down) are skipped. Empty if the expression has no
     * future run.
     */
    static Optional<Trigger> cron(String expression, ZoneId zone, TriggerBuilder<Trigger> base, Instant now) {
        TimeZone timeZone = TimeZone.getTimeZone(zone);
        try {
            CronExpression cron = new CronExpression(expression);
            cron.setTimeZone(timeZone);
            if (cron.getNextValidTimeAfter(Date.from(now)) == null) {
                return Optional.empty();
            }
        } catch (ParseException e) {
            throw new IllegalArgumentException("Invalid cron expression (should have been validated): " + expression, e);
        }
        return Optional.of(base
                .startAt(Date.from(now))
                .withSchedule(CronScheduleBuilder.cronSchedule(expression)
                        .inTimeZone(timeZone)
                        .withMisfireHandlingInstructionDoNothing())
                .build());
    }
}
