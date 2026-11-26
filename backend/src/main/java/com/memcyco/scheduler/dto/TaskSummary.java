package com.memcyco.scheduler.dto;

/** @param known false when the task key no longer exists in the code (UI shows a warning badge) */
public record TaskSummary(String key, String name, boolean known) {
}
