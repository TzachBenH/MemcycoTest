package com.memcyco.scheduler.task;

import com.memcyco.scheduler.validation.ParameterValidationResult;
import com.memcyco.scheduler.validation.ParameterValidator;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * All predefined tasks, by key. Spring injects every {@link Task} bean.
 * <p>
 * Task definitions are validated at startup (fail fast): a broken definition stops the application instead of
 * surfacing later as confusing runtime or UI errors.
 */
@Component
public class TaskRegistry {

    private static final Pattern KEY_FORMAT = Pattern.compile("^[A-Z][A-Z0-9_]{0,49}$");

    private final Map<String, Task> tasksByKey;
    private final List<Task> sortedTasks;

    public TaskRegistry(List<Task> tasks) {
        List<String> problems = new ArrayList<>();
        Map<String, Task> byKey = new LinkedHashMap<>();
        tasks.stream()
                .sorted(Comparator.comparing(Task::name))
                .forEach(task -> {
                    problems.addAll(validateDefinition(task));
                    if (byKey.putIfAbsent(task.key(), task) != null) {
                        problems.add("Duplicate task key '" + task.key() + "'");
                    }
                });
        if (!problems.isEmpty()) {
            throw new IllegalStateException("Invalid task definitions:\n - " + String.join("\n - ", problems));
        }
        this.tasksByKey = Map.copyOf(byKey);
        this.sortedTasks = List.copyOf(byKey.values());
    }

    /** All tasks, sorted by name. */
    public List<Task> all() {
        return sortedTasks;
    }

    public Optional<Task> find(String key) {
        return Optional.ofNullable(key).map(tasksByKey::get);
    }

    public Task get(String key) {
        return find(key).orElseThrow(() -> new UnknownTaskException(key));
    }

    static List<String> validateDefinition(Task task) {
        List<String> problems = new ArrayList<>();
        String key = task.key();
        String prefix = "Task '" + key + "': ";
        if (key == null || !KEY_FORMAT.matcher(key).matches()) {
            problems.add(prefix + "key must match " + KEY_FORMAT.pattern());
        }
        if (task.name() == null || task.name().isBlank()) {
            problems.add(prefix + "name is required");
        }
        Set<String> names = new HashSet<>();
        for (ParameterDefinition p : task.parameters()) {
            String where = prefix + "parameter '" + p.name() + "': ";
            if (p.name().isBlank()) {
                problems.add(prefix + "parameter name is required");
            }
            if (!names.add(p.name())) {
                problems.add(where + "duplicate name");
            }
            boolean isEnum = p.type() == ParameterType.ENUM;
            if (isEnum && p.allowedValues().isEmpty()) {
                problems.add(where + "ENUM needs allowedValues");
            }
            if (!isEnum && !p.allowedValues().isEmpty()) {
                problems.add(where + "allowedValues is only for ENUM");
            }
            if (p.type() != ParameterType.NUMBER && (p.min() != null || p.max() != null)) {
                problems.add(where + "min/max is only for NUMBER");
            }
            if (p.min() != null && p.max() != null && p.min() > p.max()) {
                problems.add(where + "min is greater than max");
            }
            if (p.defaultValue() != null) {
                ParameterValidationResult check = ParameterValidator.validate(List.of(p),
                        Map.of(p.name(), p.defaultValue()));
                if (!check.isValid()) {
                    problems.add(where + "default value " + check.errors().values());
                }
            }
        }
        return problems;
    }
}
