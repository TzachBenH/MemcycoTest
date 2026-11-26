package com.memcyco.scheduler.task;

import java.util.Map;
import java.util.stream.Collectors;

/** Parameter values do not match the task's schema at execution time. Never retried. */
public class InvalidTaskParametersException extends NonRetryableTaskException {

    private final transient Map<String, String> errors;

    public InvalidTaskParametersException(String taskKey, Map<String, String> errors) {
        super("Invalid parameters for task " + taskKey + ": " + errors.entrySet().stream()
                .map(e -> e.getKey() + " " + e.getValue())
                .collect(Collectors.joining(", ")));
        this.errors = Map.copyOf(errors);
    }

    public Map<String, String> getErrors() {
        return errors;
    }
}
