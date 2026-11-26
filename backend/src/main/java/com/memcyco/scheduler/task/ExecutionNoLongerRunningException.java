package com.memcyco.scheduler.task;

/**
 * Thrown by {@link Heartbeat#beat()} when the run's history row is no longer RUNNING (it was declared STUCK, or the
 * scheduling was deleted). The task must stop; its late result is not recorded.
 */
public class ExecutionNoLongerRunningException extends RuntimeException {

    public ExecutionNoLongerRunningException(String message) {
        super(message);
    }
}
