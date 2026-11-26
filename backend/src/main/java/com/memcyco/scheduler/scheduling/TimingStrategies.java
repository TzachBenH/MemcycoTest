package com.memcyco.scheduler.scheduling;

import com.memcyco.scheduler.entity.timing.ScheduleTiming;
import org.hibernate.Hibernate;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Selects the {@link TimingStrategy} for a timing by its class. */
@Component
public class TimingStrategies {

    private final Map<Class<?>, TimingStrategy<?>> byTimingClass;

    public TimingStrategies(List<TimingStrategy<?>> strategies) {
        this.byTimingClass = strategies.stream()
                .collect(Collectors.toUnmodifiableMap(TimingStrategy::timingClass, Function.identity()));
    }

    public String canonicalForm(ScheduleTiming timing) {
        return canonicalForm(strategyFor(timing), unproxy(timing));
    }

    public Optional<Trigger> buildTrigger(ScheduleTiming timing, TriggerBuilder<Trigger> base, Instant now) {
        return buildTrigger(strategyFor(timing), unproxy(timing), base, now);
    }

    TimingStrategy<?> strategyFor(ScheduleTiming timing) {
        TimingStrategy<?> strategy = byTimingClass.get(unproxy(timing).getClass());
        if (strategy == null) {
            throw new IllegalStateException("No TimingStrategy for " + timing.getClass().getName());
        }
        return strategy;
    }

    static ScheduleTiming unproxy(ScheduleTiming timing) {
        return (ScheduleTiming) Hibernate.unproxy(timing);
    }

    private static <T extends ScheduleTiming> String canonicalForm(TimingStrategy<T> strategy, ScheduleTiming t) {
        return strategy.canonicalForm(strategy.timingClass().cast(t));
    }

    private static <T extends ScheduleTiming> Optional<Trigger> buildTrigger(TimingStrategy<T> strategy,
                                                                          ScheduleTiming t,
                                                                          TriggerBuilder<Trigger> base, Instant now) {
        return strategy.buildTrigger(strategy.timingClass().cast(t), base, now);
    }
}
