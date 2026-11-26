package com.memcyco.scheduler.task.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class LogTaskTest {

    private final LogTask task = new LogTask();

    @Test
    void writesTheMessageAtTheDefaultInfoLevel(CapturedOutput output) throws Exception {
        task.execute(Map.of("message", "Good morning"), () -> { });

        assertThat(output).containsPattern("INFO .*\\[LOG task] Good morning");
    }

    @Test
    void writesTheMessageAtTheRequestedLevel(CapturedOutput output) throws Exception {
        task.execute(Map.of("message", "Disk almost full", "level", "ERROR"), () -> { });

        assertThat(output).containsPattern("ERROR .*\\[LOG task] Disk almost full");
    }

    @Test
    void sendsHeartbeats() throws Exception {
        AtomicInteger beats = new AtomicInteger();

        task.execute(Map.of("message", "hi"), beats::incrementAndGet);

        assertThat(beats.get()).isPositive();
    }
}
