package com.memcyco.scheduler.service;

import com.memcyco.scheduler.repository.TaskExecutionWriter;
import com.memcyco.scheduler.task.ExecutionNoLongerRunningException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DatabaseHeartbeatTest {

    private static final UUID EXECUTION = UUID.randomUUID();
    private final TaskExecutionWriter writer = mock(TaskExecutionWriter.class);
    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-26T10:00:00Z"));
    private final DatabaseHeartbeat heartbeat = new DatabaseHeartbeat(EXECUTION, writer, clock, Duration.ofSeconds(10));

    @Test
    void beatsWithinTheThrottlePeriodAreNotWritten() {
        heartbeat.beat();
        clock.advance(Duration.ofSeconds(9));
        heartbeat.beat();

        verify(writer, never()).heartbeat(any());
    }

    @Test
    void writesAtMostOncePerThrottlePeriod() {
        when(writer.heartbeat(EXECUTION)).thenReturn(true);

        clock.advance(Duration.ofSeconds(10));
        heartbeat.beat();
        heartbeat.beat();
        clock.advance(Duration.ofSeconds(10));
        heartbeat.beat();

        verify(writer, times(2)).heartbeat(EXECUTION);
    }

    @Test
    void stopsTheTaskWhenTheRunIsNoLongerRunning() {
        when(writer.heartbeat(EXECUTION)).thenReturn(false);
        clock.advance(Duration.ofSeconds(11));

        assertThatThrownBy(heartbeat::beat).isInstanceOf(ExecutionNoLongerRunningException.class);
    }

    static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
