package com.memcyco.scheduler.scheduling;

import com.memcyco.scheduler.entity.timing.DayOfWeekConverter;
import com.memcyco.scheduler.entity.timing.WeeklyTiming;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
public class WeeklyTimingStrategy implements TimingStrategy<WeeklyTiming> {

    @Override
    public Class<WeeklyTiming> timingClass() {
        return WeeklyTiming.class;
    }

    /** Same canonical form as the equivalent cron expression, so weekly and cron duplicates are detected. */
    @Override
    public String canonicalForm(WeeklyTiming timing) {
        return CronCanonicalizer.canonicalize(toQuartzCron(timing)) + " @" + timing.getTimezone();
    }

    /** A cron trigger in the timing's zone; runs missed while the system was down are skipped. */
    @Override
    public Optional<Trigger> buildTrigger(WeeklyTiming timing, TriggerBuilder<Trigger> base, Instant now) {
        return QuartzTriggers.cron(toQuartzCron(timing), timing.zoneId(), base, now);
    }

    /** E.g. MON+WED at 09:30 → {@code 0 30 9 ? * MON,WED}. */
    public static String toQuartzCron(WeeklyTiming timing) {
        String days = timing.getDays().stream()
                .sorted()
                .map(DayOfWeekConverter::toCode)
                .collect(Collectors.joining(","));
        return "%d %d %d ? * %s".formatted(timing.getTimeOfDay().getSecond(), timing.getTimeOfDay().getMinute(),
                timing.getTimeOfDay().getHour(), days);
    }
}
