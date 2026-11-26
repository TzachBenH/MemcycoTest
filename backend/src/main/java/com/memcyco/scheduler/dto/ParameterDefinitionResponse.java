package com.memcyco.scheduler.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.memcyco.scheduler.task.ParameterDefinition;
import com.memcyco.scheduler.task.ParameterType;

import java.util.List;

/** Parameter schema as the UI needs it to render and validate the field. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ParameterDefinitionResponse(String name, String label, ParameterType type, boolean required,
                                          Object defaultValue, List<String> allowedValues, Double min, Double max,
                                          String description) {

    public static ParameterDefinitionResponse from(ParameterDefinition p) {
        return new ParameterDefinitionResponse(p.name(), p.label(), p.type(), p.required(), p.defaultValue(),
                p.allowedValues().isEmpty() ? null : p.allowedValues(), p.min(), p.max(), p.description());
    }
}
