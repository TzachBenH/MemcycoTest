package com.memcyco.scheduler.exception;

import java.util.Map;

/** Base of all expected (business) errors. Mapped to an RFC 7807 response by {@link GlobalExceptionHandler}. */
public abstract class ApiException extends RuntimeException {

    private final ErrorCode code;

    protected ApiException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public ErrorCode getCode() {
        return code;
    }

    /** Extra properties added to the problem response (e.g. {@code errors}, {@code conflictingScheduling}). */
    public Map<String, Object> properties() {
        return Map.of();
    }
}
