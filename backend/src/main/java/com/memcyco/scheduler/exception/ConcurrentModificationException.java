package com.memcyco.scheduler.exception;

/** Optimistic locking: the scheduling was changed after the editor loaded it. */
public class ConcurrentModificationException extends ApiException {

    public ConcurrentModificationException() {
        super(ErrorCode.CONCURRENT_MODIFICATION,
                "This scheduling was changed by someone else. Reload to see the latest version.");
    }
}
