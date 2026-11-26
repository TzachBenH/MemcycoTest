package com.memcyco.scheduler.mapper;

import com.memcyco.scheduler.dto.timing.CronTimingDto;
import com.memcyco.scheduler.dto.timing.IntervalTimingDto;
import com.memcyco.scheduler.dto.timing.OneTimeTimingDto;
import com.memcyco.scheduler.dto.timing.TimingDto;
import com.memcyco.scheduler.dto.timing.WeeklyTimingDto;
import com.memcyco.scheduler.entity.timing.CronTiming;
import com.memcyco.scheduler.entity.timing.DayOfWeekConverter;
import com.memcyco.scheduler.entity.timing.IntervalTiming;
import com.memcyco.scheduler.entity.timing.OneTimeTiming;
import com.memcyco.scheduler.entity.timing.ScheduleTiming;
import com.memcyco.scheduler.entity.timing.WeeklyTiming;
import org.hibernate.Hibernate;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

/** Converts between the API timing shape and the timing entities. Input must already be validated. */
public final class TimingMapper {

    private TimingMapper() {
    }

    /**
     * @param now used as the default start of an interval timing (whole seconds)
     */
    public static ScheduleTiming toEntity(TimingDto dto, Instant now) {
        String zone = dto.timezone().strip();
        return switch (dto) {
            case OneTimeTimingDto t -> new OneTimeTiming(t.runAt().truncatedTo(ChronoUnit.SECONDS), zone);
            case IntervalTimingDto t -> new IntervalTiming(t.every(), t.unit(),
                    (t.startAt() != null ? t.startAt() : now).truncatedTo(ChronoUnit.SECONDS), zone);
            case WeeklyTimingDto t -> new WeeklyTiming(days(t), t.time().truncatedTo(ChronoUnit.MINUTES), zone);
            case CronTimingDto t -> new CronTiming(t.expression().strip().replaceAll("\\s+", " "), zone);
        };
    }

    public static TimingDto toDto(ScheduleTiming timing) {
        return switch ((ScheduleTiming) Hibernate.unproxy(timing)) {
            case OneTimeTiming t -> new OneTimeTimingDto(t.getRunAt(), t.getTimezone());
            case IntervalTiming t -> new IntervalTimingDto(t.getIntervalValue(), t.getIntervalUnit(), t.getStartAt(),
                    t.getTimezone());
            case WeeklyTiming t -> new WeeklyTimingDto(t.getDays().stream().sorted()
                    .map(DayOfWeekConverter::toCode).toList(), t.getTimeOfDay(), t.getTimezone());
            case CronTiming t -> new CronTimingDto(t.getCronExpression(), t.getTimezone());
            default -> throw new IllegalStateException("Unknown timing type " + timing.getClass());
        };
    }

    private static Set<DayOfWeek> days(WeeklyTimingDto dto) {
        return dto.days().stream()
                .map(code -> DayOfWeekConverter.fromCode(code.strip().toUpperCase()))
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(DayOfWeek.class)));
    }
}
