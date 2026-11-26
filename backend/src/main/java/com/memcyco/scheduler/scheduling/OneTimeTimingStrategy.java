package com.memcyco.scheduler.scheduling;

import com.memcyco.scheduler.entity.timing.OneTimeTiming;
import org.quartz.SimpleScheduleBuilder;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Optional;

@Component
public class OneTimeTimingStrategy implements TimingStrategy<OneTimeTiming> {

    @Override
    public Class<OneTimeTiming> timingClass() {
        return OneTimeTiming.class;
    }

    /** An absolute instant: the display timezone does not change when it runs. */
    @Override
    public String canonicalForm(OneTimeTiming timing) {
        return "ONE_TIME " + timing.getRunAt().truncatedTo(ChronoUnit.SECONDS);
    }

    /**
     * Fires once at {@code runAt}. If the system is down at that moment, it fires as soon as it is back.
     * A time that has already passed is not scheduled (the scheduling is COMPLETED).
     */
    @Override
    public Optional<Trigger> buildTrigger(OneTimeTiming timing, TriggerBuilder<Trigger> base, Instant now) {
        if (!timing.getRunAt().isAfter(now)) {
            return Optional.empty();
        }
        return Optional.of(base
                .startAt(Date.from(timing.getRunAt()))
                .withSchedule(SimpleScheduleBuilder.simpleSchedule().withMisfireHandlingInstructionFireNow())
                .build());
    }
}
