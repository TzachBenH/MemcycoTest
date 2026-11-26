package com.memcyco.scheduler.task.impl;

import com.memcyco.scheduler.task.Heartbeat;
import com.memcyco.scheduler.task.ParameterDefinition;
import com.memcyco.scheduler.task.ParameterType;
import com.memcyco.scheduler.task.Task;
import com.memcyco.scheduler.task.TaskParameters;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Runs one of a fixed set of read-only reports against the database and logs the result.
 * <p>
 * Users only choose a report from a list; the SQL is fixed in code and values are bound as parameters, so there
 * is no way to inject SQL.
 */
@Component
public class DbQueryTask extends Task {

    public static final String KEY = "DB_QUERY";
    static final String REPORT = "report";
    static final String LAST_HOURS = "lastHours";
    /** Blocking calls must time out well before the 5-minute stuck timeout. */
    private static final int QUERY_TIMEOUT_SECONDS = 30;

    /** The predefined reports. Each returns rows of (label, count). */
    public enum Report {
        SCHEDULINGS_BY_STATUS("Schedulings by status", """
                SELECT CASE WHEN enabled THEN 'ACTIVE' ELSE 'PAUSED' END AS label, COUNT(*) AS count
                FROM schedulings GROUP BY 1 ORDER BY 1"""),
        SCHEDULINGS_BY_TASK("Schedulings by task", """
                SELECT task_key AS label, COUNT(*) AS count
                FROM schedulings GROUP BY task_key ORDER BY task_key"""),
        EXECUTIONS_BY_STATUS("Executions by status", """
                SELECT status AS label, COUNT(*) AS count
                FROM task_executions
                WHERE started_at >= now() - make_interval(hours => CAST(:hours AS integer))
                GROUP BY status ORDER BY status""");

        private final String title;
        private final String sql;

        Report(String title, String sql) {
            this.title = title;
            this.sql = sql;
        }
    }

    private final NamedParameterJdbcTemplate jdbc;

    public DbQueryTask(DataSource dataSource) {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
        this.jdbc = new NamedParameterJdbcTemplate(jdbcTemplate);
    }

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public String name() {
        return "Database Report";
    }

    @Override
    public String description() {
        return "Runs a predefined read-only report on the database and writes the result to the log.";
    }

    @Override
    public List<ParameterDefinition> parameters() {
        return List.of(
                ParameterDefinition.builder(REPORT, ParameterType.ENUM)
                        .label("Report")
                        .required()
                        .allowedValues(Arrays.stream(Report.values()).map(Enum::name).toList())
                        .description("Which predefined report to run.")
                        .build(),
                ParameterDefinition.builder(LAST_HOURS, ParameterType.NUMBER)
                        .label("Last hours")
                        .defaultValue(24)
                        .min(1)
                        .max(720)
                        .description("Time window, used by EXECUTIONS_BY_STATUS only.")
                        .build());
    }

    @Override
    protected void doExecute(TaskParameters parameters, Heartbeat heartbeat) {
        heartbeat.beat();
        Report report = parameters.getEnum(REPORT, Report.class);
        long hours = parameters.getLong(LAST_HOURS);

        List<String> rows = jdbc.query(report.sql, new MapSqlParameterSource("hours", hours),
                (rs, i) -> rs.getString("label") + "=" + rs.getLong("count"));
        heartbeat.beat();

        String window = report == Report.EXECUTIONS_BY_STATUS ? " (last " + hours + "h)" : "";
        String result = rows.isEmpty() ? "no rows" : rows.stream().collect(Collectors.joining(", "));
        log.info("[DB_QUERY task] {}{}: {}", report.title, window, result);
        heartbeat.beat();
    }
}
