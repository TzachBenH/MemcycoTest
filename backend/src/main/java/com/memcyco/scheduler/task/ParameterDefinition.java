package com.memcyco.scheduler.task;

import java.util.List;
import java.util.Objects;

/**
 * Schema of one task parameter. The same schema is used to render the UI form, to validate values in the
 * backend, and to normalize values before computing a scheduling's fingerprint.
 *
 * @param name          key in the parameters JSON, e.g. "message"
 * @param label         human-readable label for the UI
 * @param type          value type
 * @param required      whether a value must be provided (a default value satisfies it)
 * @param defaultValue  used when no value is provided; {@code null} for none
 * @param allowedValues allowed values, only for {@link ParameterType#ENUM}; empty otherwise
 * @param min           inclusive minimum, only for {@link ParameterType#NUMBER}; {@code null} for none
 * @param max           inclusive maximum, only for {@link ParameterType#NUMBER}; {@code null} for none
 * @param description   helper text shown under the field
 */
public record ParameterDefinition(String name, String label, ParameterType type, boolean required,
                                  Object defaultValue, List<String> allowedValues, Double min, Double max,
                                  String description) {

    public ParameterDefinition {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(type, "type");
        label = label == null ? name : label;
        allowedValues = allowedValues == null ? List.of() : List.copyOf(allowedValues);
        description = description == null ? "" : description;
    }

    public static Builder builder(String name, ParameterType type) {
        return new Builder(name, type);
    }

    /** Fluent builder so task definitions read like a form description. */
    public static final class Builder {
        private final String name;
        private final ParameterType type;
        private String label;
        private boolean required;
        private Object defaultValue;
        private List<String> allowedValues = List.of();
        private Double min;
        private Double max;
        private String description;

        private Builder(String name, ParameterType type) {
            this.name = name;
            this.type = type;
        }

        public Builder label(String label) {
            this.label = label;
            return this;
        }

        public Builder required() {
            this.required = true;
            return this;
        }

        public Builder defaultValue(Object defaultValue) {
            this.defaultValue = defaultValue;
            return this;
        }

        public Builder allowedValues(List<String> allowedValues) {
            this.allowedValues = allowedValues;
            return this;
        }

        public Builder min(double min) {
            this.min = min;
            return this;
        }

        public Builder max(double max) {
            this.max = max;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public ParameterDefinition build() {
            return new ParameterDefinition(name, label, type, required, defaultValue, allowedValues, min, max,
                    description);
        }
    }
}
