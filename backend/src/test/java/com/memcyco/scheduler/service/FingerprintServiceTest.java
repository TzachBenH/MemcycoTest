package com.memcyco.scheduler.service;

import com.memcyco.scheduler.entity.IntervalUnit;
import com.memcyco.scheduler.entity.timing.CronTiming;
import com.memcyco.scheduler.entity.timing.IntervalTiming;
import com.memcyco.scheduler.entity.timing.OneTimeTiming;
import com.memcyco.scheduler.entity.timing.ScheduleTiming;
import com.memcyco.scheduler.entity.timing.WeeklyTiming;
import com.memcyco.scheduler.scheduling.CronTimingStrategy;
import com.memcyco.scheduler.scheduling.IntervalTimingStrategy;
import com.memcyco.scheduler.scheduling.OneTimeTimingStrategy;
import com.memcyco.scheduler.scheduling.TimingStrategies;
import com.memcyco.scheduler.scheduling.WeeklyTimingStrategy;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static java.time.DayOfWeek.MONDAY;
import static java.time.DayOfWeek.WEDNESDAY;
import static org.assertj.core.api.Assertions.assertThat;

/** The duplicate-detection rules agreed in the design. */
class FingerprintServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-26T12:00:00Z");
    private static final String TZ = "Asia/Jerusalem";
    private static final Map<String, Object> PARAMS = Map.of("message", "hi", "level", "INFO");

    private final FingerprintService service = new FingerprintService(new TimingStrategies(List.of(
            new OneTimeTimingStrategy(), new WeeklyTimingStrategy(), new CronTimingStrategy(),
            new IntervalTimingStrategy(Clock.fixed(NOW, ZoneOffset.UTC)))));

    private String fp(ScheduleTiming timing) {
        return service.fingerprint("LOG", PARAMS, timing);
    }

    private static WeeklyTiming weekly(Set<DayOfWeek> days, String time, String zone) {
        return new WeeklyTiming(days, LocalTime.parse(time), zone);
    }

    @Test
    void weeklyAndEquivalentCronAreDuplicates() {
        assertThat(fp(weekly(Set.of(MONDAY), "09:00", TZ))).isEqualTo(fp(new CronTiming("0 0 9 ? * MON", TZ)));
        assertThat(fp(weekly(Set.of(MONDAY, WEDNESDAY), "09:30", TZ)))
                .isEqualTo(fp(new CronTiming("0 30 9 ? * WED,MON", TZ)));
    }

    @Test
    void differentTimezonesAreDifferent() {
        assertThat(fp(weekly(Set.of(MONDAY), "09:00", TZ))).isNotEqualTo(fp(weekly(Set.of(MONDAY), "09:00", "UTC")));
    }

    @Test
    void intervalStartIsPartOfTheIdentity() {
        var from10 = new IntervalTiming(15, IntervalUnit.MINUTES, Instant.parse("2026-11-26T10:00:00Z"), TZ);
        var from1005 = new IntervalTiming(15, IntervalUnit.MINUTES, Instant.parse("2026-11-26T10:05:00Z"), TZ);

        assertThat(fp(from10)).isNotEqualTo(fp(from1005));
    }

    @Test
    void sixtyMinutesEqualsOneHour() {
        var start = Instant.parse("2026-11-26T10:00:00Z");
        assertThat(fp(new IntervalTiming(60, IntervalUnit.MINUTES, start, "UTC")))
                .isEqualTo(fp(new IntervalTiming(1, IntervalUnit.HOURS, start, "UTC")));
    }

    @Test
    void alignedPastIntervalEqualsEquivalentCron() {
        var every15FromTenOClock = new IntervalTiming(15, IntervalUnit.MINUTES, Instant.parse("2026-11-26T10:00:00Z"), TZ);

        assertThat(fp(every15FromTenOClock)).isEqualTo(fp(new CronTiming("0 */15 * * * ?", TZ)));
    }

    @Test
    void futureIntervalIsNotEqualToCron() {
        var startsTomorrow = new IntervalTiming(15, IntervalUnit.MINUTES, Instant.parse("2026-11-27T10:00:00Z"), TZ);

        assertThat(fp(startsTomorrow)).isNotEqualTo(fp(new CronTiming("0 */15 * * * ?", TZ)));
    }

    @Test
    void partialOverlapIsNotADuplicate() {
        var start = Instant.parse("2026-11-26T10:00:00Z");
        assertThat(fp(new IntervalTiming(5, IntervalUnit.MINUTES, start, TZ)))
                .isNotEqualTo(fp(new IntervalTiming(15, IntervalUnit.MINUTES, start, TZ)));
    }

    @Test
    void oneTimeIgnoresDisplayTimezone() {
        var at = Instant.parse("2026-12-01T14:00:00Z");
        assertThat(fp(new OneTimeTiming(at, TZ))).isEqualTo(fp(new OneTimeTiming(at, "UTC")));
    }

    @Test
    void parameterOrderDoesNotMatterButValuesDo() {
        var timing = new CronTiming("0 0 9 ? * MON", TZ);
        Map<String, Object> reordered = new TreeMap<>(java.util.Comparator.reverseOrder());
        reordered.putAll(PARAMS);

        assertThat(service.fingerprint("LOG", reordered, timing)).isEqualTo(fp(timing));
        assertThat(service.fingerprint("LOG", Map.of("message", "bye", "level", "INFO"), timing)).isNotEqualTo(fp(timing));
        assertThat(service.fingerprint("DUMMY_EMAIL", PARAMS, timing)).isNotEqualTo(fp(timing));
    }

    @Test
    void runKeyDependsOnlyOnTaskAndParameters() {
        assertThat(service.runKey("LOG", PARAMS)).isEqualTo(service.runKey("LOG", new TreeMap<>(PARAMS)));
        assertThat(service.runKey("LOG", PARAMS)).isNotEqualTo(service.runKey("LOG", Map.of("message", "x")));
        assertThat(service.runKey("LOG", PARAMS)).hasSize(64);
    }
}
