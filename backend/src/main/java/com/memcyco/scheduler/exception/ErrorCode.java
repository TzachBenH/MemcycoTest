package com.memcyco.scheduler.exception;

import org.springframework.http.HttpStatus;

/** Machine-readable error codes returned in the {@code code} field of every error response. */
public enum ErrorCode {
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Invalid request"),
    NOT_FOUND(HttpStatus.NOT_FOUND, "Not found"),
    DUPLICATE_SCHEDULING(HttpStatus.CONFLICT, "Duplicate scheduling"),
    EDIT_LOCKED(HttpStatus.CONFLICT, "Being edited by someone else"),
    EDIT_LOCK_LOST(HttpStatus.CONFLICT, "Edit lock not held"),
    CONCURRENT_MODIFICATION(HttpStatus.CONFLICT, "Modified by someone else"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error");

    private final HttpStatus status;
    private final String title;

    ErrorCode(HttpStatus status, String title) {
        this.status = status;
        this.title = title;
    }

    public HttpStatus status() {
        return status;
    }

    public String title() {
        return title;
    }
}
