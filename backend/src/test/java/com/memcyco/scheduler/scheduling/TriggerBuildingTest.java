package com.memcyco.scheduler.scheduling;

import com.memcyco.scheduler.entity.IntervalUnit;
import com.memcyco.scheduler.entity.timing.CronTiming;
import com.memcyco.scheduler.entity.timing.IntervalTiming;
import com.memcyco.scheduler.entity.timing.OneTimeTiming;
import com.memcyco.scheduler.entity.timing.WeeklyTiming;
import org.junit.jupiter.api.Test;
import org.quartz.CronTrigger;
import org.quartz.SimpleTrigger;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Each timing type builds the right Quartz trigger with the agreed misfire rule. */
class TriggerBuildingTest {

    private static final Instant NOW = Instant.parse("2026-11-26T12:00:00Z"); // a Thursday
    private static final String TZ = "Asia/Jerusalem"; // UTC+2 in winter

    private static TriggerBuilder<Trigger> base() {
        return TriggerBuilder.newTrigger().withIdentity("test");
    }

    @Test
    void oneTimeFiresOnceAndRunsOnRecoveryIfMissed() {
        Instant runAt = NOW.plus(Duration.ofHours(2));

        SimpleTrigger trigger = (SimpleTrigger) new OneTimeTimingStrategy()
                .buildTrigger(new OneTimeTiming(runAt, TZ), base(), NOW).orElseThrow();

        assertThat(trigger.getStartTime()).isEqualTo(Date.from(runAt));
        assertThat(trigger.getRepeatCount()).isZero();
        assertThat(trigger.getMisfireInstruction()).isEqualTo(SimpleTrigger.MISFIRE_INSTRUCTION_FIRE_NOW);
    }

    @Test
    void oneTimeInThePastIsNotScheduled() {
        assertThat(new OneTimeTimingStrategy().buildTrigger(new OneTimeTiming(NOW.minusSeconds(1), TZ), base(), NOW))
                .isEmpty();
    }

    @Test
    void weeklyBecomesACronTriggerInItsTimezoneThatSkipsMissedRuns() {
        WeeklyTiming weekly = new WeeklyTiming(Set.of(DayOfWeek.WEDNESDAY, DayOfWeek.MONDAY), LocalTime.of(9, 0), TZ);

        CronTrigger trigger = (CronTrigger) new WeeklyTimingStrategy().buildTrigger(weekly, base(), NOW).orElseThrow();

        assertThat(trigger.getCronExpression()).isEqualTo("0 0 9 ? * MON,WED");
        assertThat(trigger.getTimeZone().getID()).isEqualTo(TZ);
        assertThat(trigger.getMisfireInstruction()).isEqualTo(CronTrigger.MISFIRE_INSTRUCTION_DO_NOTHING);
        // next Monday 09:00 in Jerusalem = 07:00 UTC
        assertThat(trigger.getFireTimeAfter(Date.from(NOW))).isEqualTo(Date.from(Instant.parse("2026-11-30T07:00:00Z")));
    }

    @Test
    void cronWithoutFutureRunsIsNotScheduled() {
        assertThat(new CronTimingStrategy().buildTrigger(new CronTiming("0 0 9 1 1 ? 2020", TZ), base(), NOW))
                .isEmpty();
    }

    @Test
    void intervalRepeatsForeverOnItsGridAndSkipsMissedRuns() {
        Instant start = Instant.parse("2026-11-26T10:00:00Z");
        IntervalTimingStrategy strategy = new IntervalTimingStrategy(Clock.fixed(NOW, ZoneOffset.UTC));

        SimpleTrigger trigger = (SimpleTrigger) strategy
                .buildTrigger(new IntervalTiming(15, IntervalUnit.MINUTES, start, TZ), base(), NOW.plusSeconds(30))
                .orElseThrow();

        assertThat(trigger.getRepeatInterval()).isEqualTo(Duration.ofMinutes(15).toMillis());
        assertThat(trigger.getRepeatCount()).isEqualTo(SimpleTrigger.REPEAT_INDEFINITELY);
        assertThat(trigger.getMisfireInstruction())
                .isEqualTo(SimpleTrigger.MISFIRE_INSTRUCTION_RESCHEDULE_NEXT_WITH_REMAINING_COUNT);
        assertThat(trigger.getStartTime()).isEqualTo(Date.from(Instant.parse("2026-11-26T12:15:00Z")));
    }

    @Test
    void nextAlignedRunKeepsTheGridDefinedByTheStart() {
        Instant start = Instant.parse("2026-11-26T10:00:00Z");
        Duration every15 = Duration.ofMinutes(15);

        // a start of "now" runs immediately
        assertThat(IntervalTimingStrategy.nextAlignedRun(NOW, every15, NOW.plusMillis(300))).isEqualTo(NOW);
        // a future start is kept
        assertThat(IntervalTimingStrategy.nextAlignedRun(NOW.plusSeconds(600), every15, NOW)).isEqualTo(NOW.plusSeconds(600));
        // resumed at 10:15:30 → next grid point 10:30 (10:15 was missed and is skipped)
        assertThat(IntervalTimingStrategy.nextAlignedRun(start, every15, Instant.parse("2026-11-26T10:15:30Z")))
                .isEqualTo(Instant.parse("2026-11-26T10:30:00Z"));
    }
}
