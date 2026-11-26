package com.memcyco.scheduler.dto.timing;

import java.time.Instant;

public record OneTimeTimingDto(Instant runAt, String timezone) implements TimingDto {
}
