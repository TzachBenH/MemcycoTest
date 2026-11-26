package com.memcyco.scheduler.mapper;

import com.memcyco.scheduler.dto.SchedulingResponse;
import com.memcyco.scheduler.dto.SchedulingStatus;
import com.memcyco.scheduler.entity.Scheduling;
import com.memcyco.scheduler.entity.timing.CronTiming;
import com.memcyco.scheduler.entity.timing.OneTimeTiming;
import com.memcyco.scheduler.entity.timing.ScheduleTiming;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** The status shown in the table, and how a scheduling whose task no longer exists is presented. */
class SchedulingMapperTest {

    private static final Instant NOW = Instant.parse("2026-11-26T12:00:00Z");

    @Test
    void aDisabledSchedulingIsPaused() {
        assertThat(SchedulingMapper.status(scheduling(false, new CronTiming("0 0 9 * * ?", "UTC")), NOW))
                .isEqualTo(SchedulingStatus.PAUSED);
        assertThat(SchedulingMapper.status(scheduling(false, new OneTimeTiming(NOW.minusSeconds(60), "UTC")), NOW))
                .isEqualTo(SchedulingStatus.PAUSED);
    }

    @Test
    void anEnabledOneTimeSchedulingIsCompletedOnceItsRunTimeHasPassed() {
        assertThat(SchedulingMapper.status(scheduling(true, new OneTimeTiming(NOW.plusSeconds(60), "UTC")), NOW))
                .isEqualTo(SchedulingStatus.ACTIVE);
        assertThat(SchedulingMapper.status(scheduling(true, new OneTimeTiming(NOW, "UTC")), NOW))
                .isEqualTo(SchedulingStatus.COMPLETED);
        assertThat(SchedulingMapper.status(scheduling(true, new OneTimeTiming(NOW.minusSeconds(60), "UTC")), NOW))
                .isEqualTo(SchedulingStatus.COMPLETED);
    }

    @Test
    void anEnabledRecurringSchedulingIsActive() {
        assertThat(SchedulingMapper.status(scheduling(true, new CronTiming("0 0 9 * * ?", "UTC")), NOW))
                .isEqualTo(SchedulingStatus.ACTIVE);
    }

    @Test
    void aTaskThatNoLongerExistsIsShownByItsKeyAndFlaggedUnknown() {
        SchedulingResponse response = SchedulingMapper.toResponse(
                scheduling(true, new CronTiming("0 0 9 * * ?", "UTC")), Optional.empty(), null, null, List.of(),
                null, NOW);

        assertThat(response.task().key()).isEqualTo("REMOVED_TASK");
        assertThat(response.task().name()).isEqualTo("REMOVED_TASK");
        assertThat(response.task().known()).isFalse();
        assertThat(response.lastExecution()).isNull();
        assertThat(response.timing().timezone()).isEqualTo("UTC");
    }

    private static Scheduling scheduling(boolean enabled, ScheduleTiming timing) {
        return new Scheduling("Test", "REMOVED_TASK", Map.of(), enabled, timing, "fingerprint");
    }
}
