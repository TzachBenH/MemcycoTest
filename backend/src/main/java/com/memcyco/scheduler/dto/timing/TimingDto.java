package com.memcyco.scheduler.dto.timing;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * API shape of a scheduling's timing. The {@code type} property selects the variant:
 * <pre>
 * { "type": "ONE_TIME", "runAt": "2026-12-01T14:00:00Z",           "timezone": "Asia/Jerusalem" }
 * { "type": "INTERVAL", "every": 15, "unit": "MINUTES", "startAt": null, "timezone": "Asia/Jerusalem" }
 * { "type": "WEEKLY",   "days": ["MON","WED"], "time": "09:00",     "timezone": "Asia/Jerusalem" }
 * { "type": "CRON",     "expression": "0 0 9 ? * MON",              "timezone": "Asia/Jerusalem" }
 * </pre>
 * Fields are validated by {@code validation.TimingValidator} (all errors collected), not by Jackson.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = OneTimeTimingDto.class, name = "ONE_TIME"),
        @JsonSubTypes.Type(value = IntervalTimingDto.class, name = "INTERVAL"),
        @JsonSubTypes.Type(value = WeeklyTimingDto.class, name = "WEEKLY"),
        @JsonSubTypes.Type(value = CronTimingDto.class, name = "CRON")
})
public sealed interface TimingDto permits OneTimeTimingDto, IntervalTimingDto, WeeklyTimingDto, CronTimingDto {

    /** IANA timezone id, e.g. "Asia/Jerusalem". */
    String timezone();
}
