package com.memcyco.scheduler.validation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @param parameters normalized values (sorted keys, defaults applied); only meaningful when {@link #isValid()}
 * @param errors     field path ({@code parameters.<name>}) → message
 */
public record ParameterValidationResult(Map<String, Object> parameters, Map<String, String> errors) {

    public ParameterValidationResult {
        // keep the (sorted) insertion order of the validator
        parameters = Collections.unmodifiableMap(new LinkedHashMap<>(parameters));
        errors = Collections.unmodifiableMap(new LinkedHashMap<>(errors));
    }

    public boolean isValid() {
        return errors.isEmpty();
    }
}
