package com.memcyco.scheduler.service;

import com.memcyco.scheduler.repository.TaskExecutionWriter;
import com.memcyco.scheduler.task.ExecutionNoLongerRunningException;
import com.memcyco.scheduler.task.Heartbeat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * The heartbeat a running task calls. Writes {@code last_heartbeat_at = now()} at most once per throttle period
 * (tasks may beat in tight loops). When the write finds the run is no longer RUNNING (declared STUCK, or the
 * scheduling was deleted) it throws, which stops the task.
 */
final class DatabaseHeartbeat implements Heartbeat {

    private final UUID executionId;
    private final TaskExecutionWriter executions;
    private final Clock clock;
    private final Duration throttle;
    /** Inserting the RUNNING row counts as the first beat. */
    private Instant lastWrite;

    DatabaseHeartbeat(UUID executionId, TaskExecutionWriter executions, Clock clock, Duration throttle) {
        this.executionId = executionId;
        this.executions = executions;
        this.clock = clock;
        this.throttle = throttle;
        this.lastWrite = clock.instant();
    }

    @Override
    public synchronized void beat() {
        Instant now = clock.instant();
        if (Duration.between(lastWrite, now).compareTo(throttle) < 0) {
            return;
        }
        lastWrite = now;
        if (!executions.heartbeat(executionId)) {
            throw new ExecutionNoLongerRunningException("Execution " + executionId
                    + " is no longer running (it was marked STUCK or its scheduling was deleted)");
        }
    }
}
