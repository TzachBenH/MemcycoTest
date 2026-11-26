package com.memcyco.scheduler.task;

/**
 * Thrown by a task for a failure that will not go away by retrying (e.g. invalid input).
 * The attempt is recorded as FAILED and no retry is scheduled.
 */
public class NonRetryableTaskException extends RuntimeException {

    public NonRetryableTaskException(String message) {
        super(message);
    }

    public NonRetryableTaskException(String message, Throwable cause) {
        super(message, cause);
    }
}
