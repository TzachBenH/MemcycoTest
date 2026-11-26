package com.memcyco.scheduler.scheduling;

import com.memcyco.scheduler.entity.timing.CronTiming;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;

@Component
public class CronTimingStrategy implements TimingStrategy<CronTiming> {

    @Override
    public Class<CronTiming> timingClass() {
        return CronTiming.class;
    }

    @Override
    public String canonicalForm(CronTiming timing) {
        return CronCanonicalizer.canonicalize(timing.getCronExpression()) + " @" + timing.getTimezone();
    }

    /** Runs missed while the system was down are skipped. */
    @Override
    public Optional<Trigger> buildTrigger(CronTiming timing, TriggerBuilder<Trigger> base, Instant now) {
        return QuartzTriggers.cron(timing.getCronExpression(), timing.zoneId(), base, now);
    }
}
