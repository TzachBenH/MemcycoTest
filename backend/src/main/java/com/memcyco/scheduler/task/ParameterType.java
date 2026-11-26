package com.memcyco.scheduler.task;

/** Type of a task parameter. Drives the UI input, backend validation and value normalization. */
public enum ParameterType {
    /** Single-line text. */
    STRING,
    /** Multi-line text. */
    TEXT,
    /** Integer or decimal; may have min/max. */
    NUMBER,
    BOOLEAN,
    /** One of {@link ParameterDefinition#allowedValues()}. */
    ENUM,
    EMAIL
}
