package com.memcyco.scheduler.validation;

import com.memcyco.scheduler.dto.timing.CronTimingDto;
import com.memcyco.scheduler.dto.timing.IntervalTimingDto;
import com.memcyco.scheduler.dto.timing.OneTimeTimingDto;
import com.memcyco.scheduler.dto.timing.TimingDto;
import com.memcyco.scheduler.dto.timing.WeeklyTimingDto;
import org.quartz.CronExpression;

import java.text.ParseException;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

/** Validates a timing from the API. All errors are collected, keyed by field path {@code timing.<field>}. */
public final class TimingValidator {

    static final int MAX_INTERVAL = 10_000;
    static final int MAX_CRON_LENGTH = 120;
    private static final Set<String> DAY_CODES = Set.of("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN");
    private static final String PREFIX = "timing.";

    private TimingValidator() {
    }

    public static Map<String, String> validate(TimingDto timing, Instant now) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (timing == null) {
            errors.put("timing", "is required");
            return errors;
        }
        ZoneId zone = validateZone(timing.timezone(), errors);
        switch (timing) {
            case OneTimeTimingDto t -> validateOneTime(t, now, errors);
            case IntervalTimingDto t -> validateInterval(t, errors);
            case WeeklyTimingDto t -> validateWeekly(t, errors);
            case CronTimingDto t -> validateCron(t, zone, now, errors);
        }
        return errors;
    }

    private static ZoneId validateZone(String timezone, Map<String, String> errors) {
        if (timezone == null || timezone.isBlank()) {
            errors.put(PREFIX + "timezone", "is required");
            return null;
        }
        try {
            return ZoneId.of(timezone.strip());
        } catch (DateTimeException e) {
            errors.put(PREFIX + "timezone", "is not a valid timezone");
            return null;
        }
    }

    private static void validateOneTime(OneTimeTimingDto t, Instant now, Map<String, String> errors) {
        if (t.runAt() == null) {
            errors.put(PREFIX + "runAt", "is required");
        } else if (!t.runAt().isAfter(now)) {
            errors.put(PREFIX + "runAt", "must be in the future");
        }
    }

    private static void validateInterval(IntervalTimingDto t, Map<String, String> errors) {
        if (t.every() == null) {
            errors.put(PREFIX + "every", "is required");
        } else if (t.every() < 1 || t.every() > MAX_INTERVAL) {
            errors.put(PREFIX + "every", "must be between 1 and " + MAX_INTERVAL);
        }
        if (t.unit() == null) {
            errors.put(PREFIX + "unit", "is required");
        }
    }

    private static void validateWeekly(WeeklyTimingDto t, Map<String, String> errors) {
        if (t.days() == null || t.days().isEmpty()) {
            errors.put(PREFIX + "days", "select at least one day");
        } else if (t.days().stream().anyMatch(d -> d == null || !DAY_CODES.contains(d.strip().toUpperCase()))) {
            errors.put(PREFIX + "days", "must contain only MON, TUE, WED, THU, FRI, SAT, SUN");
        }
        if (t.time() == null) {
            errors.put(PREFIX + "time", "is required");
        }
    }

    private static void validateCron(CronTimingDto t, ZoneId zone, Instant now, Map<String, String> errors) {
        String field = PREFIX + "expression";
        if (t.expression() == null || t.expression().isBlank()) {
            errors.put(field, "is required");
            return;
        }
        String expression = t.expression().strip();
        if (expression.length() > MAX_CRON_LENGTH) {
            errors.put(field, "must be at most " + MAX_CRON_LENGTH + " characters");
            return;
        }
        if (expression.split("\\s+").length < 6) {
            errors.put(field, "must be a Quartz cron expression with 6 or 7 fields (seconds first), "
                    + "e.g. 0 0 9 ? * MON");
            return;
        }
        try {
            CronExpression cron = new CronExpression(expression);
            if (zone != null) {
                cron.setTimeZone(TimeZone.getTimeZone(zone));
                if (cron.getNextValidTimeAfter(Date.from(now)) == null) {
                    errors.put(field, "never runs in the future");
                }
            }
        } catch (ParseException e) {
            errors.put(field, "is not a valid Quartz cron expression: " + e.getMessage());
        }
    }
}
