package com.memcyco.scheduler.exception;

import java.time.Instant;
import java.util.Map;

/** Another editor holds the scheduling's edit lock. */
public class EditLockedException extends ApiException {

    private final String lockedBy;
    private final Instant since;

    public EditLockedException(String lockedBy, Instant since) {
        super(ErrorCode.EDIT_LOCKED, "This scheduling is being edited by another user or in another tab");
        this.lockedBy = lockedBy;
        this.since = since;
    }

    @Override
    public Map<String, Object> properties() {
        return Map.of("lockedBy", lockedBy, "since", since);
    }
}
