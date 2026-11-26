package com.memcyco.scheduler.task;

/**
 * Liveness signal from a running task.
 * <p>
 * <b>Every task must call {@link #beat()}</b> when it starts, between steps, and inside long loops, so that the
 * time between two beats never exceeds the stuck timeout (5 minutes). A run that stops beating is marked STUCK
 * by the stuck-execution monitor. Any blocking call (HTTP, DB, ...) must use a timeout shorter than that.
 * <p>
 * Implementations are cheap to call often (writes are throttled). If the run is no longer RUNNING (declared
 * stuck, or its scheduling was deleted), {@code beat()} throws {@link ExecutionNoLongerRunningException} so the
 * task stops.
 */
@FunctionalInterface
public interface Heartbeat {

    void beat();
}
