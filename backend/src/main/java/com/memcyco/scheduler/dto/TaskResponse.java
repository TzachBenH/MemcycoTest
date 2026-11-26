package com.memcyco.scheduler.dto;

import com.memcyco.scheduler.task.Task;

import java.util.List;

/** A predefined task and its parameter schema. */
public record TaskResponse(String key, String name, String description,
                           List<ParameterDefinitionResponse> parameters) {

    public static TaskResponse from(Task task) {
        return new TaskResponse(task.key(), task.name(), task.description(),
                task.parameters().stream().map(ParameterDefinitionResponse::from).toList());
    }
}
