package com.memcyco.scheduler.dto.timing;

/** @param expression Quartz cron syntax (6–7 fields including seconds), e.g. "0 0 9 ? * MON" */
public record CronTimingDto(String expression, String timezone) implements TimingDto {
}
