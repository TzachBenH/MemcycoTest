package com.memcyco.scheduler.support;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Request bodies for the schedulings API, as mutable maps so a test can tweak a field before sending. */
public final class Requests {

    public static final String UTC = "UTC";

    private Requests() {
    }

    public static Map<String, Object> scheduling(String name, String taskKey, Map<String, Object> parameters,
                                                 Map<String, Object> timing) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        body.put("taskKey", taskKey);
        body.put("parameters", new LinkedHashMap<>(parameters));
        body.put("timing", timing);
        return body;
    }

    public static Map<String, Object> logScheduling(String name, String message, Map<String, Object> timing) {
        return scheduling(name, "LOG", Map.of("message", message), timing);
    }

    public static Map<String, Object> controllable(String name, ControllableTask.Scenario scenario, String label,
                                                   Map<String, Object> timing) {
        return scheduling(name, ControllableTask.KEY,
                Map.of(ControllableTask.SCENARIO, scenario.name(), ControllableTask.LABEL, label), timing);
    }

    public static Map<String, Object> cron(String expression) {
        return cron(expression, UTC);
    }

    public static Map<String, Object> cron(String expression, String timezone) {
        return timing("CRON", "expression", expression, "timezone", timezone);
    }

    /** A timing that does not fire during a test run (once a year, on January 1st); varied by the hour. */
    public static Map<String, Object> yearly(int hour) {
        return cron("0 0 " + hour + " 1 1 ?");
    }

    public static Map<String, Object> weekly(List<String> days, String time, String timezone) {
        return timing("WEEKLY", "days", days, "time", time, "timezone", timezone);
    }

    public static Map<String, Object> interval(int every, String unit) {
        return timing("INTERVAL", "every", every, "unit", unit, "timezone", UTC);
    }

    public static Map<String, Object> oneTime(Instant runAt) {
        return timing("ONE_TIME", "runAt", runAt.toString(), "timezone", UTC);
    }

    private static Map<String, Object> timing(String type, Object... keysAndValues) {
        Map<String, Object> timing = new LinkedHashMap<>();
        timing.put("type", type);
        for (int i = 0; i < keysAndValues.length; i += 2) {
            timing.put((String) keysAndValues[i], keysAndValues[i + 1]);
        }
        return timing;
    }
}
