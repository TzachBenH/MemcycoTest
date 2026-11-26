package com.memcyco.scheduler.scheduling;

import com.memcyco.scheduler.entity.timing.IntervalTiming;
import org.quartz.SimpleScheduleBuilder;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.Optional;

@Component
public class IntervalTimingStrategy implements TimingStrategy<IntervalTiming> {

    private static final long MINUTES_PER_HOUR = 60;
    private static final long HOURS_PER_DAY = 24;
    /** A start this close to "now" still counts as "now" (e.g. the default start set while handling the request). */
    static final Duration START_TOLERANCE = Duration.ofSeconds(5);

    private final Clock clock;

    public IntervalTimingStrategy(Clock clock) {
        this.clock = clock;
    }

    @Override
    public Class<IntervalTiming> timingClass() {
        return IntervalTiming.class;
    }

    /**
     * The interval and its start are both part of the identity ("every 15 min from 10:00" ≠ "from 10:05").
     * When the interval can be expressed exactly as a cron expression, the cron canonical form is used instead,
     * so it also matches an equivalent cron scheduling (see {@link #equivalentCron}).
     */
    @Override
    public String canonicalForm(IntervalTiming timing) {
        return equivalentCron(timing)
                .map(cron -> CronCanonicalizer.canonicalize(cron) + " @" + timing.getTimezone())
                .orElseGet(() -> "INTERVAL %ds from %s".formatted(timing.interval().toSeconds(),
                        timing.getStartAt()));
    }

    /**
     * A cron expression that fires at exactly the same moments, if one exists:
     * <ul>
     *     <li>the start time has already passed (a cron also fires before a future start)</li>
     *     <li>minutes that divide an hour (1, 2, 3, 4, 5, 6, 10, 12, 15, 20, 30), e.g. every 15 min from 10:07:00 →
     *         {@code 0 7/15 * * * ?}</li>
     *     <li>hours that divide a day (1, 2, 3, 4, 6, 8, 12, 24), only in zones without daylight saving time
     *         (a local-time cron skips/repeats an hour on DST changes, a fixed interval does not)</li>
     * </ul>
     */
    Optional<String> equivalentCron(IntervalTiming timing) {
        if (timing.getStartAt().isAfter(clock.instant()) || timing.getStartAt().getNano() != 0) {
            return Optional.empty();
        }
        ZoneId zone = timing.zoneId();
        ZonedDateTime start = timing.getStartAt().atZone(zone);
        long minutes = timing.interval().toMinutes();

        if (minutes < MINUTES_PER_HOUR && MINUTES_PER_HOUR % minutes == 0) {
            return Optional.of("%d %d/%d * * * ?".formatted(start.getSecond(), start.getMinute() % minutes, minutes));
        }
        boolean wholeHours = minutes % MINUTES_PER_HOUR == 0;
        long hours = minutes / MINUTES_PER_HOUR;
        if (wholeHours && HOURS_PER_DAY % hours == 0 && zone.getRules().isFixedOffset()) {
            return Optional.of("%d %d %d/%d * * ?".formatted(start.getSecond(), start.getMinute(),
                    start.getHour() % hours, hours));
        }
        return Optional.empty();
    }

    /**
     * Repeats forever every N minutes/hours on the grid defined by {@code startAt}. When (re)scheduled after the
     * start time, the first run is the next grid point, so runs missed while paused or down are skipped; a start of
     * "now" runs immediately.
     */
    @Override
    public Optional<Trigger> buildTrigger(IntervalTiming timing, TriggerBuilder<Trigger> base, Instant now) {
        Instant firstRun = nextAlignedRun(timing.getStartAt(), timing.interval(), now);
        return Optional.of(base
                .startAt(Date.from(firstRun))
                .withSchedule(SimpleScheduleBuilder.simpleSchedule()
                        .withIntervalInMilliseconds(timing.interval().toMillis())
                        .repeatForever()
                        .withMisfireHandlingInstructionNextWithRemainingCount())
                .build());
    }

    /** The first point of {@code start + k * interval} (k ≥ 0) that is not before {@code now - tolerance}. */
    static Instant nextAlignedRun(Instant start, Duration interval, Instant now) {
        Instant earliest = now.minus(START_TOLERANCE);
        if (!start.isBefore(earliest)) {
            return start;
        }
        long elapsed = Duration.between(start, earliest).toMillis();
        long periods = elapsed / interval.toMillis() + 1;
        return start.plusMillis(periods * interval.toMillis());
    }
}
