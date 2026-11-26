package com.memcyco.scheduler.validation;

import com.memcyco.scheduler.entity.timing.ScheduleTiming;

import java.util.Map;

/** A create/update request that passed validation: trimmed name, normalized parameters, timing entity. */
public record ValidatedScheduling(String name, String taskKey, Map<String, Object> parameters,
                                  ScheduleTiming timing, boolean enabled) {
}
