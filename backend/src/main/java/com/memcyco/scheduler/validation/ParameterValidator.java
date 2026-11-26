package com.memcyco.scheduler.validation;

import com.memcyco.scheduler.task.ParameterDefinition;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Validates parameter values against a task's schema and returns them <b>normalized</b>:
 * <ul>
 *     <li>keys sorted (so {@code {"a":1,"b":2}} equals {@code {"b":2,"a":1}})</li>
 *     <li>defaults filled in for missing values</li>
 *     <li>text trimmed; blank text treated as "not provided"</li>
 *     <li>numbers as {@code Long} when integral, otherwise {@code Double} (so 5 equals 5.0)</li>
 * </ul>
 * Normalized values are what gets stored, passed to the task, and hashed into the scheduling fingerprint.
 * All errors are collected (not fail-fast), keyed by field path {@code parameters.<name>} to match the UI form.
 */
public final class ParameterValidator {

    public static final String FIELD_PREFIX = "parameters.";
    static final int MAX_STRING_LENGTH = 500;
    static final int MAX_TEXT_LENGTH = 5000;
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private ParameterValidator() {
    }

    public static ParameterValidationResult validate(List<ParameterDefinition> definitions,
                                                     Map<String, ?> rawValues) {
        Map<String, ?> input = rawValues == null ? Map.of() : rawValues;
        Map<String, String> errors = new LinkedHashMap<>();
        Map<String, Object> normalized = new TreeMap<>();

        Set<String> known = definitions.stream().map(ParameterDefinition::name).collect(Collectors.toSet());
        input.keySet().stream()
                .filter(key -> !known.contains(key))
                .sorted()
                .forEach(key -> errors.put(FIELD_PREFIX + key, "is not a parameter of this task"));

        for (ParameterDefinition definition : definitions) {
            Object value = blankToNull(input.get(definition.name()));
            if (value == null) {
                value = definition.defaultValue();
            }
            if (value == null) {
                if (definition.required()) {
                    errors.put(FIELD_PREFIX + definition.name(), "is required");
                }
                continue;
            }
            try {
                normalized.put(definition.name(), normalize(definition, value));
            } catch (InvalidValueException e) {
                errors.put(FIELD_PREFIX + definition.name(), e.getMessage());
            }
        }
        return new ParameterValidationResult(normalized, errors);
    }

    private static Object blankToNull(Object value) {
        return value instanceof String s && s.isBlank() ? null : value;
    }

    private static Object normalize(ParameterDefinition definition, Object value) {
        return switch (definition.type()) {
            case STRING -> text(value, MAX_STRING_LENGTH);
            case TEXT -> text(value, MAX_TEXT_LENGTH);
            case EMAIL -> email(value);
            case NUMBER -> number(definition, value);
            case BOOLEAN -> bool(value);
            case ENUM -> enumValue(definition, value);
        };
    }

    private static String text(Object value, int maxLength) {
        if (!(value instanceof String s)) {
            throw new InvalidValueException("must be text");
        }
        String trimmed = s.strip();
        if (trimmed.length() > maxLength) {
            throw new InvalidValueException("must be at most " + maxLength + " characters");
        }
        return trimmed;
    }

    private static String email(Object value) {
        String text = text(value, MAX_STRING_LENGTH);
        if (!EMAIL.matcher(text).matches()) {
            throw new InvalidValueException("must be a valid email address");
        }
        return text;
    }

    private static Number number(ParameterDefinition definition, Object value) {
        BigDecimal number;
        try {
            number = value instanceof Number n ? new BigDecimal(n.toString()) : new BigDecimal(value.toString().strip());
        } catch (NumberFormatException e) {
            throw new InvalidValueException("must be a number");
        }
        if (definition.min() != null && number.compareTo(BigDecimal.valueOf(definition.min())) < 0) {
            throw new InvalidValueException("must be at least " + format(definition.min()));
        }
        if (definition.max() != null && number.compareTo(BigDecimal.valueOf(definition.max())) > 0) {
            throw new InvalidValueException("must be at most " + format(definition.max()));
        }
        BigDecimal stripped = number.stripTrailingZeros();
        return stripped.scale() <= 0 ? (Number) stripped.longValueExact() : (Number) stripped.doubleValue();
    }

    private static Boolean bool(Object value) {
        if (value instanceof Boolean b) {
            return b;
        }
        if (value instanceof String s && (s.equalsIgnoreCase("true") || s.equalsIgnoreCase("false"))) {
            return Boolean.valueOf(s);
        }
        throw new InvalidValueException("must be true or false");
    }

    private static String enumValue(ParameterDefinition definition, Object value) {
        if (!(value instanceof String s) || !definition.allowedValues().contains(s.strip())) {
            throw new InvalidValueException("must be one of: " + String.join(", ", definition.allowedValues()));
        }
        return s.strip();
    }

    private static String format(double d) {
        return d == Math.rint(d) ? Long.toString((long) d) : Double.toString(d);
    }

    private static final class InvalidValueException extends RuntimeException {
        InvalidValueException(String message) {
            super(message, null, false, false);
        }
    }
}
