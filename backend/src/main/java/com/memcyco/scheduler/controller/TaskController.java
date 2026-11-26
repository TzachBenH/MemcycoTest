package com.memcyco.scheduler.controller;

import com.memcyco.scheduler.dto.TaskResponse;
import com.memcyco.scheduler.task.TaskRegistry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Read-only access to the predefined tasks. Tasks are defined in code and cannot be created or edited. */
@RestController
@RequestMapping("/api/tasks")
@Tag(name = "Tasks", description = "Predefined, read-only tasks that schedulings can run")
public class TaskController {

    private final TaskRegistry taskRegistry;

    public TaskController(TaskRegistry taskRegistry) {
        this.taskRegistry = taskRegistry;
    }

    @GetMapping
    @Operation(summary = "List all predefined tasks with their parameter schemas")
    public List<TaskResponse> list() {
        return taskRegistry.all().stream().map(TaskResponse::from).toList();
    }
}
