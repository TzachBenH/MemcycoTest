package com.memcyco.scheduler.service;

import com.memcyco.scheduler.task.ExecutionNoLongerRunningException;
import com.memcyco.scheduler.task.NonRetryableTaskException;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Fixed retry policy, the same for every task (agreed: not configurable):
 * 3 attempts in total, exponential backoff 10s → 20s, ±20% random jitter so that many failures at once do not
 * all retry at the same second.
 */
@Component
public class RetryPolicy {

    /** Total attempts, including the first run. */
    public static final int MAX_ATTEMPTS = 3;
    static final Duration BASE_DELAY = Duration.ofSeconds(10);
    static final int MULTIPLIER = 2;
    static final double JITTER = 0.2;

    /**
     * Delay before the given attempt: 10s before attempt 2, 20s before attempt 3 (each ±20%).
     * Not final only so integration tests can use millisecond delays.
     */
    public Duration delayBeforeAttempt(int attempt) {
        long baseMillis = BASE_DELAY.toMillis() * (long) Math.pow(MULTIPLIER, attempt - 2.0);
        double jitter = 1 + ThreadLocalRandom.current().nextDouble(-JITTER, JITTER);
        return Duration.ofMillis(Math.round(baseMillis * jitter));
    }

    /** Failures that a retry cannot fix (invalid input, unknown task) or runs that were stopped are not retried. */
    public boolean isRetryable(Throwable failure) {
        return !(failure instanceof NonRetryableTaskException)
                && !(failure instanceof ExecutionNoLongerRunningException);
    }

    public boolean shouldRetry(Throwable failure, int failedAttempt) {
        return failedAttempt < MAX_ATTEMPTS && isRetryable(failure);
    }
}
