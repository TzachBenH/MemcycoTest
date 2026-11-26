package com.memcyco.scheduler.exception;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** An active scheduling with the same task, parameters and timing already exists. */
public class DuplicateSchedulingException extends ApiException {

    private final UUID conflictingId;
    private final String conflictingName;

    public DuplicateSchedulingException(UUID conflictingId, String conflictingName) {
        super(ErrorCode.DUPLICATE_SCHEDULING, conflictingName == null
                ? "An active scheduling with the same task, parameters and timing already exists"
                : "\"" + conflictingName + "\" is already scheduled with the same task, parameters and timing");
        this.conflictingId = conflictingId;
        this.conflictingName = conflictingName;
    }

    @Override
    public Map<String, Object> properties() {
        if (conflictingId == null) {
            return Map.of();
        }
        Map<String, Object> conflict = new LinkedHashMap<>();
        conflict.put("id", conflictingId);
        conflict.put("name", conflictingName);
        return Map.of("conflictingScheduling", conflict);
    }
}
