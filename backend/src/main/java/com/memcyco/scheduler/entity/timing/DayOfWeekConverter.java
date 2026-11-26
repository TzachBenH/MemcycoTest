package com.memcyco.scheduler.entity.timing;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.time.DayOfWeek;
import java.time.format.TextStyle;
import java.util.Arrays;
import java.util.Locale;

/** Stores {@link DayOfWeek} as the 3-letter code used in the DB check constraint and in Quartz ("MON".."SUN"). */
@Converter
public class DayOfWeekConverter implements AttributeConverter<DayOfWeek, String> {

    public static String toCode(DayOfWeek day) {
        return day.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).toUpperCase(Locale.ROOT);
    }

    public static DayOfWeek fromCode(String code) {
        return Arrays.stream(DayOfWeek.values())
                .filter(d -> toCode(d).equals(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown day of week: " + code));
    }

    @Override
    public String convertToDatabaseColumn(DayOfWeek day) {
        return day == null ? null : toCode(day);
    }

    @Override
    public DayOfWeek convertToEntityAttribute(String code) {
        return code == null ? null : fromCode(code);
    }
}
