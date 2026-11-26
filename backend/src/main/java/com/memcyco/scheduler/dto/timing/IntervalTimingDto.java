package com.memcyco.scheduler.dto.timing;

import com.memcyco.scheduler.entity.IntervalUnit;

import java.time.Instant;

/** @param startAt optional on input; defaults to "now" (whole seconds) when creating. */
public record IntervalTimingDto(Integer every, IntervalUnit unit, Instant startAt, String timezone)
        implements TimingDto {
}
