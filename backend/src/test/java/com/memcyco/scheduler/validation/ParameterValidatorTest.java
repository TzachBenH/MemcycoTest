package com.memcyco.scheduler.validation;

import com.memcyco.scheduler.task.ParameterDefinition;
import com.memcyco.scheduler.task.ParameterType;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

class ParameterValidatorTest {

    private static final ParameterDefinition MESSAGE = ParameterDefinition.builder("message", ParameterType.STRING)
            .required().build();
    private static final ParameterDefinition LEVEL = ParameterDefinition.builder("level", ParameterType.ENUM)
            .allowedValues(List.of("INFO", "WARN")).defaultValue("INFO").build();
    private static final ParameterDefinition COUNT = ParameterDefinition.builder("count", ParameterType.NUMBER)
            .min(1).max(10).build();
    private static final ParameterDefinition EMAIL = ParameterDefinition.builder("to", ParameterType.EMAIL).build();
    private static final ParameterDefinition FLAG = ParameterDefinition.builder("flag", ParameterType.BOOLEAN).build();

    private static ParameterValidationResult validate(Map<String, ?> values, ParameterDefinition... definitions) {
        return ParameterValidator.validate(List.of(definitions), values);
    }

    @Test
    void missingRequiredValueIsAnError() {
        var result = validate(Map.of(), MESSAGE);

        assertThat(result.isValid()).isFalse();
        assertThat(result.errors()).containsExactly(entry("parameters.message", "is required"));
    }

    @Test
    void blankTextCountsAsMissing() {
        var result = validate(Map.of("message", "   "), MESSAGE);

        assertThat(result.errors()).containsEntry("parameters.message", "is required");
    }

    @Test
    void defaultIsAppliedWhenValueIsMissing() {
        var result = validate(Map.of("message", "hi"), MESSAGE, LEVEL);

        assertThat(result.isValid()).isTrue();
        assertThat(result.parameters()).containsEntry("level", "INFO");
    }

    @Test
    void textIsTrimmed() {
        var result = validate(Map.of("message", "  hello  "), MESSAGE);

        assertThat(result.parameters()).containsEntry("message", "hello");
    }

    @Test
    void unknownParameterIsAnError() {
        var result = validate(Map.of("message", "hi", "extra", 1), MESSAGE);

        assertThat(result.errors()).containsEntry("parameters.extra", "is not a parameter of this task");
    }

    @Test
    void numbersAreNormalizedSoEqualValuesLookEqual() {
        assertThat(validate(Map.of("count", 5.0), COUNT).parameters()).containsEntry("count", 5L);
        assertThat(validate(Map.of("count", "7"), COUNT).parameters()).containsEntry("count", 7L);
        assertThat(validate(Map.of("count", 2.5), COUNT).parameters()).containsEntry("count", 2.5);
    }

    @Test
    void numberOutsideMinMaxIsAnError() {
        assertThat(validate(Map.of("count", 0), COUNT).errors()).containsEntry("parameters.count", "must be at least 1");
        assertThat(validate(Map.of("count", 11), COUNT).errors()).containsEntry("parameters.count", "must be at most 10");
        assertThat(validate(Map.of("count", "abc"), COUNT).errors()).containsEntry("parameters.count", "must be a number");
    }

    @Test
    void enumValueMustBeAllowed() {
        var result = validate(Map.of("level", "DEBUG"), LEVEL);

        assertThat(result.errors()).containsEntry("parameters.level", "must be one of: INFO, WARN");
    }

    @Test
    void emailMustLookLikeAnEmail() {
        assertThat(validate(Map.of("to", "not-an-email"), EMAIL).errors())
                .containsEntry("parameters.to", "must be a valid email address");
        assertThat(validate(Map.of("to", "a@b.com"), EMAIL).isValid()).isTrue();
    }

    @Test
    void booleanAcceptsBooleansAndTrueFalseStrings() {
        assertThat(validate(Map.of("flag", "TRUE"), FLAG).parameters()).containsEntry("flag", true);
        assertThat(validate(Map.of("flag", false), FLAG).parameters()).containsEntry("flag", false);
        assertThat(validate(Map.of("flag", "yes"), FLAG).errors()).containsEntry("parameters.flag", "must be true or false");
    }

    @Test
    void textMustBeAString() {
        assertThat(validate(Map.of("message", 42), MESSAGE).errors()).containsEntry("parameters.message", "must be text");
    }

    @Test
    void allErrorsAreCollected() {
        Map<String, Object> values = new HashMap<>();
        values.put("count", 99);
        values.put("to", "bad");

        var result = validate(values, MESSAGE, COUNT, EMAIL);

        assertThat(result.errors()).containsOnlyKeys("parameters.message", "parameters.count", "parameters.to");
    }

    @Test
    void normalizedKeysAreSorted() {
        var result = validate(Map.of("message", "hi", "count", 3), MESSAGE, COUNT);

        assertThat(result.parameters()).containsExactly(entry("count", 3L), entry("message", "hi"));
    }

    @Test
    void nullValuesMapIsTreatedAsEmpty() {
        assertThat(ParameterValidator.validate(List.of(LEVEL), null).parameters()).containsEntry("level", "INFO");
    }
}
