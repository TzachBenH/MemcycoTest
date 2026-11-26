package com.memcyco.scheduler.validation;

import com.memcyco.scheduler.dto.timing.CronTimingDto;
import com.memcyco.scheduler.dto.timing.IntervalTimingDto;
import com.memcyco.scheduler.dto.timing.OneTimeTimingDto;
import com.memcyco.scheduler.dto.timing.WeeklyTimingDto;
import com.memcyco.scheduler.entity.IntervalUnit;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TimingValidatorTest {

    private static final Instant NOW = Instant.parse("2026-11-26T12:00:00Z");
    private static final String TZ = "Asia/Jerusalem";

    @Test
    void missingTimingIsAnError() {
        assertThat(TimingValidator.validate(null, NOW)).containsEntry("timing", "is required");
    }

    @Test
    void timezoneMustBeValid() {
        assertThat(TimingValidator.validate(new CronTimingDto("0 0 9 ? * MON", "Mars/Olympus"), NOW))
                .containsEntry("timing.timezone", "is not a valid timezone");
        assertThat(TimingValidator.validate(new CronTimingDto("0 0 9 ? * MON", null), NOW))
                .containsEntry("timing.timezone", "is required");
    }

    @Test
    void oneTimeMustBeInTheFuture() {
        assertThat(TimingValidator.validate(new OneTimeTimingDto(NOW.minusSeconds(1), TZ), NOW))
                .containsEntry("timing.runAt", "must be in the future");
        assertThat(TimingValidator.validate(new OneTimeTimingDto(null, TZ), NOW))
                .containsEntry("timing.runAt", "is required");
        assertThat(TimingValidator.validate(new OneTimeTimingDto(NOW.plusSeconds(60), TZ), NOW)).isEmpty();
    }

    @Test
    void intervalNeedsAPositiveAmountAndAUnit() {
        assertThat(TimingValidator.validate(new IntervalTimingDto(0, null, null, TZ), NOW))
                .containsEntry("timing.every", "must be between 1 and 10000")
                .containsEntry("timing.unit", "is required");
        assertThat(TimingValidator.validate(new IntervalTimingDto(15, IntervalUnit.MINUTES, null, TZ), NOW)).isEmpty();
    }

    @Test
    void weeklyNeedsValidDaysAndATime() {
        assertThat(TimingValidator.validate(new WeeklyTimingDto(List.of(), null, TZ), NOW))
                .containsEntry("timing.days", "select at least one day")
                .containsEntry("timing.time", "is required");
        assertThat(TimingValidator.validate(new WeeklyTimingDto(List.of("MON", "XYZ"), LocalTime.NOON, TZ), NOW))
                .containsKey("timing.days");
        assertThat(TimingValidator.validate(new WeeklyTimingDto(List.of("mon"), LocalTime.NOON, TZ), NOW)).isEmpty();
    }

    @Test
    void cronMustBeValidQuartzSyntax() {
        assertThat(TimingValidator.validate(new CronTimingDto("0 9 * * 1", TZ), NOW).get("timing.expression"))
                .contains("6 or 7 fields");
        assertThat(TimingValidator.validate(new CronTimingDto("0 0 25 ? * MON", TZ), NOW).get("timing.expression"))
                .startsWith("is not a valid Quartz cron expression");
        assertThat(TimingValidator.validate(new CronTimingDto("0 0 9 ? * MON", TZ), NOW)).isEmpty();
    }

    @Test
    void cronThatNeverFiresAgainIsRejected() {
        assertThat(TimingValidator.validate(new CronTimingDto("0 0 9 1 1 ? 2020", TZ), NOW))
                .containsEntry("timing.expression", "never runs in the future");
    }
}
