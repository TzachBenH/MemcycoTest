package com.memcyco.scheduler.validation;

import com.memcyco.scheduler.dto.SchedulingRequest;
import com.memcyco.scheduler.dto.timing.OneTimeTimingDto;
import com.memcyco.scheduler.entity.timing.OneTimeTiming;
import com.memcyco.scheduler.entity.timing.ScheduleTiming;
import com.memcyco.scheduler.exception.ValidationException;
import com.memcyco.scheduler.mapper.TimingMapper;
import com.memcyco.scheduler.task.Task;
import com.memcyco.scheduler.task.TaskRegistry;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Validates a whole create/update request in one pass and reports <b>all</b> problems at once:
 * basic fields → task + parameters (schema) → timing. Duplicate detection happens afterwards in the service.
 */
@Component
public class SchedulingRequestValidator {

    static final int MAX_NAME_LENGTH = 100;

    private final TaskRegistry taskRegistry;
    private final Clock clock;

    public SchedulingRequestValidator(TaskRegistry taskRegistry, Clock clock) {
        this.taskRegistry = taskRegistry;
        this.clock = clock;
    }

    /**
     * @param existingTiming the scheduling's current timing when updating, otherwise {@code null}. An unchanged
     *                       one-time run time may be in the past (e.g. renaming a completed scheduling).
     */
    public ValidatedScheduling validate(SchedulingRequest request, ScheduleTiming existingTiming) {
        Map<String, String> errors = new LinkedHashMap<>();
        Instant now = clock.instant();

        String name = request.name() == null ? "" : request.name().strip();
        if (name.isEmpty()) {
            errors.put("name", "is required");
        } else if (name.length() > MAX_NAME_LENGTH) {
            errors.put("name", "must be at most " + MAX_NAME_LENGTH + " characters");
        }

        Optional<Task> task = Optional.empty();
        if (request.taskKey() == null || request.taskKey().isBlank()) {
            errors.put("taskKey", "is required");
        } else {
            task = taskRegistry.find(request.taskKey().strip());
            if (task.isEmpty()) {
                errors.put("taskKey", "is not a known task");
            }
        }
        ParameterValidationResult parameters = task
                .map(t -> ParameterValidator.validate(t.parameters(), request.parameters()))
                .orElse(null);
        if (parameters != null) {
            errors.putAll(parameters.errors());
        }

        Map<String, String> timingErrors = TimingValidator.validate(request.timing(), now);
        if (isUnchangedPastRunTime(request, existingTiming)) {
            timingErrors.remove("timing.runAt");
        }
        errors.putAll(timingErrors);

        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
        return new ValidatedScheduling(name, task.get().key(), parameters.parameters(),
                TimingMapper.toEntity(request.timing(), now), request.enabledOrDefault());
    }

    private static boolean isUnchangedPastRunTime(SchedulingRequest request, ScheduleTiming existing) {
        return existing != null
                && request.timing() instanceof OneTimeTimingDto requested
                && requested.runAt() != null
                && Hibernate.unproxy(existing) instanceof OneTimeTiming current
                && current.getRunAt().equals(requested.runAt());
    }
}
