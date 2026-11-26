package com.memcyco.scheduler.task;

import java.util.Map;
import java.util.Optional;

/**
 * Typed, read-only access to a run's parameter values. Values were already validated and normalized against the
 * task's schema, so required parameters are present and have the declared type.
 */
public final class TaskParameters {

    private final Map<String, Object> values;

    public TaskParameters(Map<String, Object> values) {
        this.values = Map.copyOf(values);
    }

    public String getString(String name) {
        return getOptionalString(name).orElseThrow(() -> missing(name));
    }

    public Optional<String> getOptionalString(String name) {
        return Optional.ofNullable(values.get(name)).map(Object::toString);
    }

    public long getLong(String name) {
        return getOptionalNumber(name).orElseThrow(() -> missing(name)).longValue();
    }

    public double getDouble(String name) {
        return getOptionalNumber(name).orElseThrow(() -> missing(name)).doubleValue();
    }

    public Optional<Number> getOptionalNumber(String name) {
        return Optional.ofNullable((Number) values.get(name));
    }

    public boolean getBoolean(String name) {
        return Optional.ofNullable((Boolean) values.get(name)).orElseThrow(() -> missing(name));
    }

    public <E extends Enum<E>> E getEnum(String name, Class<E> type) {
        return Enum.valueOf(type, getString(name));
    }

    public Map<String, Object> asMap() {
        return values;
    }

    private static IllegalStateException missing(String name) {
        return new IllegalStateException("Parameter '" + name + "' has no value");
    }
}
