package com.memcyco.scheduler.scheduling;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Rewrites a <b>valid</b> Quartz cron expression into a canonical form, so that expressions with the same meaning
 * become the same string:
 * <ul>
 *     <li>names become numbers ({@code MON} → 2, {@code JAN} → 1)</li>
 *     <li>lists, ranges and steps are expanded and sorted ({@code 1-3} → {@code 1,2,3},
 *         {@code *}{@code /15} → {@code 0,15,30,45}, {@code WED,MON} → {@code 2,4})</li>
 *     <li>{@code ?} and any full range become {@code *}; a missing year becomes {@code *}</li>
 *     <li>special Quartz tokens ({@code L}, {@code W}, {@code #}) are kept as they are (names → numbers)</li>
 * </ul>
 * Output: 7 space-separated fields (second minute hour day-of-month month day-of-week year).
 */
public final class CronCanonicalizer {

    private record Field(int min, int max, Map<String, Integer> names) {
        int size() {
            return max - min + 1;
        }
    }

    private static final Map<String, Integer> MONTHS = Map.ofEntries(
            Map.entry("JAN", 1), Map.entry("FEB", 2), Map.entry("MAR", 3), Map.entry("APR", 4),
            Map.entry("MAY", 5), Map.entry("JUN", 6), Map.entry("JUL", 7), Map.entry("AUG", 8),
            Map.entry("SEP", 9), Map.entry("OCT", 10), Map.entry("NOV", 11), Map.entry("DEC", 12));
    /** Quartz numbering: SUN = 1 ... SAT = 7. */
    private static final Map<String, Integer> DAYS = Map.of(
            "SUN", 1, "MON", 2, "TUE", 3, "WED", 4, "THU", 5, "FRI", 6, "SAT", 7);

    private static final List<Field> FIELDS = List.of(
            new Field(0, 59, Map.of()),      // second
            new Field(0, 59, Map.of()),      // minute
            new Field(0, 23, Map.of()),      // hour
            new Field(1, 31, Map.of()),      // day of month
            new Field(1, 12, MONTHS),        // month
            new Field(1, 7, DAYS),           // day of week
            new Field(1970, 2099, Map.of())  // year
    );

    private CronCanonicalizer() {
    }

    public static String canonicalize(String expression) {
        String[] parts = expression.strip().toUpperCase(Locale.ROOT).split("\\s+");
        if (parts.length < 6 || parts.length > 7) {
            throw new IllegalArgumentException("A Quartz cron expression has 6 or 7 fields: " + expression);
        }
        try {
            StringBuilder canonical = new StringBuilder();
            for (int i = 0; i < FIELDS.size(); i++) {
                String part = i < parts.length ? parts[i] : "*";
                canonical.append(i == 0 ? "" : " ").append(canonicalField(FIELDS.get(i), part));
            }
            return canonical.toString();
        } catch (RuntimeException unusualSyntax) {
            // Valid for Quartz but not understood here: fall back to a whitespace/case-normalized form.
            return "RAW " + String.join(" ", parts);
        }
    }

    private static String canonicalField(Field field, String text) {
        if (text.equals("*") || text.equals("?")) {
            return "*";
        }
        // Names first: "WED" and "JUL" contain W / L, which must not be mistaken for the special tokens below.
        String numeric = replaceNames(field, text);
        if (numeric.matches(".*[LW#].*")) {
            return numeric;
        }
        TreeSet<Integer> values = new TreeSet<>();
        for (String item : numeric.split(",")) {
            expand(field, item, values);
        }
        if (values.size() == field.size()) {
            return "*";
        }
        return values.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    private static void expand(Field field, String item, TreeSet<Integer> values) {
        String range = item;
        int step = 1;
        int slash = item.indexOf('/');
        if (slash >= 0) {
            range = item.substring(0, slash);
            step = Integer.parseInt(item.substring(slash + 1));
            if (step <= 0) {
                throw new IllegalArgumentException("step must be positive");
            }
        }
        int start;
        int end;
        if (range.equals("*")) {
            start = field.min();
            end = field.max();
        } else if (range.contains("-")) {
            String[] bounds = range.split("-", 2);
            start = value(field, bounds[0]);
            end = value(field, bounds[1]);
        } else {
            start = value(field, range);
            end = slash >= 0 ? field.max() : start;
        }
        // Supports wrap-around ranges such as 22-2 (hours) or FRI-MON.
        int size = field.size();
        int length = Math.floorMod(end - start, size);
        for (int offset = 0; offset <= length; offset += step) {
            values.add(field.min() + Math.floorMod(start - field.min() + offset, size));
        }
    }

    private static int value(Field field, String token) {
        Integer named = field.names().get(token);
        int value = named != null ? named : Integer.parseInt(token);
        if (value < field.min() || value > field.max()) {
            throw new IllegalArgumentException("value out of range: " + token);
        }
        return value;
    }

    private static String replaceNames(Field field, String text) {
        String result = text;
        for (Map.Entry<String, Integer> name : field.names().entrySet()) {
            result = result.replace(name.getKey(), String.valueOf(name.getValue()));
        }
        return result;
    }
}
