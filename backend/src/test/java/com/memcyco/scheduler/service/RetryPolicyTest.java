package com.memcyco.scheduler.service;

import com.memcyco.scheduler.task.ExecutionNoLongerRunningException;
import com.memcyco.scheduler.task.InvalidTaskParametersException;
import com.memcyco.scheduler.task.NonRetryableTaskException;
import com.memcyco.scheduler.task.UnknownTaskException;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RetryPolicyTest {

    private final RetryPolicy policy = new RetryPolicy();

    @RepeatedTest(20)
    void delaysDoubleWithTwentyPercentJitter() {
        assertThat(policy.delayBeforeAttempt(2)).isBetween(Duration.ofSeconds(8), Duration.ofSeconds(12));
        assertThat(policy.delayBeforeAttempt(3)).isBetween(Duration.ofSeconds(16), Duration.ofSeconds(24));
    }

    @Test
    void threeAttemptsInTotal() {
        RuntimeException failure = new IllegalStateException("SMTP timeout");

        assertThat(policy.shouldRetry(failure, 1)).isTrue();
        assertThat(policy.shouldRetry(failure, 2)).isTrue();
        assertThat(policy.shouldRetry(failure, 3)).isFalse();
    }

    @Test
    void permanentFailuresAreNotRetried() {
        assertThat(policy.shouldRetry(new NonRetryableTaskException("bad input"), 1)).isFalse();
        assertThat(policy.shouldRetry(new InvalidTaskParametersException("LOG", Map.of("x", "y")), 1)).isFalse();
        assertThat(policy.shouldRetry(new UnknownTaskException("GONE"), 1)).isFalse();
        assertThat(policy.shouldRetry(new ExecutionNoLongerRunningException("stuck"), 1)).isFalse();
    }
}
