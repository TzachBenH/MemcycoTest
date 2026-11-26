package com.memcyco.scheduler.task;

/** Wraps the real heartbeat to count beats, so the base {@link Task} can warn about tasks that never beat. */
final class CountingHeartbeat implements Heartbeat {

    private final Heartbeat delegate;
    private int count;

    CountingHeartbeat(Heartbeat delegate) {
        this.delegate = delegate;
    }

    @Override
    public void beat() {
        count++;
        delegate.beat();
    }

    int count() {
        return count;
    }
}
