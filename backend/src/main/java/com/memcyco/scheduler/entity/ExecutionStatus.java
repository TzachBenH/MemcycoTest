package com.memcyco.scheduler.entity;

/** Status of one execution attempt in {@code task_executions}. */
public enum ExecutionStatus {
    /** Started and holds the runtime lock; must send heartbeats. */
    RUNNING,
    SUCCESS,
    FAILED,
    /** Not started: the same scheduling was already running the same task + parameters. */
    SKIPPED,
    /** No heartbeat for longer than the timeout; marked by the stuck-execution monitor. */
    STUCK;

    public boolean isFinished() {
        return this != RUNNING;
    }
}
