package com.memcyco.scheduler.dto;

import java.util.UUID;

/** Non-blocking notice attached to a scheduling response, e.g. "a paused duplicate exists". */
public record WarningResponse(String code, String message, UUID schedulingId, String schedulingName) {

    public static final String PAUSED_DUPLICATE_EXISTS = "PAUSED_DUPLICATE_EXISTS";
    public static final String ACTIVE_DUPLICATE_EXISTS = "ACTIVE_DUPLICATE_EXISTS";
}
