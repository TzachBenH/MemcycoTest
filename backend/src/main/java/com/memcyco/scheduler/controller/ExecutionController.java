package com.memcyco.scheduler.controller;

import com.memcyco.scheduler.dto.ExecutionResponse;
import com.memcyco.scheduler.dto.PageResponse;
import com.memcyco.scheduler.entity.ExecutionStatus;
import com.memcyco.scheduler.exception.ValidationException;
import com.memcyco.scheduler.service.ExecutionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/schedulings/{id}/executions")
@Tag(name = "Execution history")
public class ExecutionController {

    private static final int MAX_PAGE_SIZE = 100;

    private final ExecutionService executions;

    public ExecutionController(ExecutionService executions) {
        this.executions = executions;
    }

    @GetMapping
    @Operation(summary = "Execution history of a scheduling, newest first")
    public PageResponse<ExecutionResponse> history(@PathVariable UUID id,
                                                   @RequestParam(required = false) ExecutionStatus status,
                                                   @RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "20") int size) {
        if (page < 0) {
            throw ValidationException.of("page", "must be 0 or more");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw ValidationException.of("size", "must be between 1 and " + MAX_PAGE_SIZE);
        }
        return executions.history(id, status, page, size);
    }
}
