package com.memcyco.scheduler.task.impl;

import com.memcyco.scheduler.task.InvalidTaskParametersException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(OutputCaptureExtension.class)
class DummyEmailTaskTest {

    private final DummyEmailTask task = new DummyEmailTask();

    @Test
    void simulatesSendingAndLogsIt(CapturedOutput output) throws Exception {
        AtomicInteger beats = new AtomicInteger();

        task.execute(Map.of("to", "a@test.com", "subject", "Report", "body", "Hello"), beats::incrementAndGet);

        assertThat(output).contains("Email sent | to=a@test.com | subject=Report | body=5 chars");
        assertThat(beats.get()).isGreaterThanOrEqualTo(3);
    }

    @Test
    void failingDomainSimulatesAnSmtpOutage() {
        assertThatThrownBy(() -> task.execute(Map.of("to", "x@fail.example", "subject", "S"), () -> { }))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Simulated SMTP failure");
    }

    @Test
    void invalidRecipientIsRejected() {
        assertThatThrownBy(() -> task.execute(Map.of("to", "nope", "subject", "S"), () -> { }))
                .isInstanceOf(InvalidTaskParametersException.class)
                .hasMessageContaining("parameters.to must be a valid email address");
    }
}
