package com.memcyco.scheduler.exception;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Invalid input. {@code errors} maps form field paths (e.g. {@code parameters.message}) to messages. */
public class ValidationException extends ApiException {

    private final Map<String, String> errors;

    public ValidationException(Map<String, String> errors) {
        super(ErrorCode.VALIDATION_FAILED, "The request has " + errors.size() + " invalid field(s)");
        this.errors = Collections.unmodifiableMap(new LinkedHashMap<>(errors));
    }

    public static ValidationException of(String field, String message) {
        return new ValidationException(Map.of(field, message));
    }

    public Map<String, String> getErrors() {
        return errors;
    }

    @Override
    public Map<String, Object> properties() {
        return Map.of("errors", errors);
    }
}
