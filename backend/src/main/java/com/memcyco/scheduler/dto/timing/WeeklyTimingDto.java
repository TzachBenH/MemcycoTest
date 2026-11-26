package com.memcyco.scheduler.dto.timing;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalTime;
import java.util.List;

/** @param days 3-letter day codes: MON, TUE, WED, THU, FRI, SAT, SUN */
public record WeeklyTimingDto(List<String> days,
                              @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "HH:mm") LocalTime time,
                              String timezone) implements TimingDto {
}
