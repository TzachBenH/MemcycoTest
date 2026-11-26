package com.memcyco.scheduler.dto;

import com.memcyco.scheduler.dto.timing.TimingDto;

import java.util.Map;

/**
 * Create / full-replace request.
 *
 * @param enabled defaults to {@code true} when omitted
 * @param version required on update (the version the editor loaded) for optimistic locking; ignored on create
 */
public record SchedulingRequest(String name, String taskKey, Map<String, Object> parameters, TimingDto timing,
                                Boolean enabled, Long version) {

    public boolean enabledOrDefault() {
        return enabled == null || enabled;
    }
}
