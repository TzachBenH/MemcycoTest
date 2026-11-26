package com.memcyco.scheduler.support;

import com.memcyco.scheduler.service.RetryPolicy;

import java.time.Duration;

/**
 * The production retry rules (3 attempts, which failures are retried) with a short, fixed delay, so integration
 * tests do not wait 10 + 20 seconds. Tests that must act while a retry is pending set a longer delay.
 */
public class FastRetryPolicy extends RetryPolicy {

    public static final Duration DEFAULT_DELAY = Duration.ofMillis(300);

    private volatile Duration delay = DEFAULT_DELAY;

    @Override
    public Duration delayBeforeAttempt(int attempt) {
        return delay;
    }

    public void setDelay(Duration delay) {
        this.delay = delay;
    }

    public void reset() {
        delay = DEFAULT_DELAY;
    }
}
