package com.memcyco.scheduler.scheduling;

/** A Quartz operation failed. Unexpected; results in a 500 and rolls back the surrounding transaction. */
public class SchedulingEngineException extends RuntimeException {

    public SchedulingEngineException(String message, Throwable cause) {
        super(message, cause);
    }
}
