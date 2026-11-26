package com.memcyco.scheduler.scheduling;

import com.memcyco.scheduler.entity.timing.ScheduleTiming;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;

import java.time.Instant;
import java.util.Optional;

/**
 * Everything that is specific to one timing type lives in one strategy (polymorphism instead of switch statements):
 * the canonical form used for duplicate detection, and building the Quartz trigger.
 * Adding a timing type = adding a {@link ScheduleTiming} subclass and one strategy.
 */
public interface TimingStrategy<T extends ScheduleTiming> {

    Class<T> timingClass();

    /**
     * A string that is equal for two timings exactly when they run at the same set of moments, regardless of how
     * they were written (e.g. weekly "MON 09:00" and cron "0 0 9 ? * MON" in the same zone are equal).
     */
    String canonicalForm(T timing);

    /**
     * The Quartz trigger for this timing, with the agreed misfire rules, or empty when it will never run again
     * (a one-time scheduling whose time has passed, or a cron expression without a future run).
     *
     * @param base trigger builder with identity, job and job data already set
     * @param now  current time; runs in the past are never fired by (re)scheduling
     */
    Optional<Trigger> buildTrigger(T timing, TriggerBuilder<Trigger> base, Instant now);
}
