package com.memcyco.scheduler.task.impl;

import com.memcyco.scheduler.task.Heartbeat;
import com.memcyco.scheduler.task.ParameterDefinition;
import com.memcyco.scheduler.task.ParameterType;
import com.memcyco.scheduler.task.Task;
import com.memcyco.scheduler.task.TaskParameters;
import org.slf4j.event.Level;
import org.springframework.stereotype.Component;

import java.util.List;

/** Writes a message to the application log. */
@Component
public class LogTask extends Task {

    public static final String KEY = "LOG";
    static final String MESSAGE = "message";
    static final String LEVEL = "level";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public String name() {
        return "Log Message";
    }

    @Override
    public String description() {
        return "Writes a message to the application log.";
    }

    @Override
    public List<ParameterDefinition> parameters() {
        return List.of(
                ParameterDefinition.builder(MESSAGE, ParameterType.STRING)
                        .label("Log message")
                        .required()
                        .description("The text to write to the log.")
                        .build(),
                ParameterDefinition.builder(LEVEL, ParameterType.ENUM)
                        .label("Level")
                        .allowedValues(List.of("INFO", "WARN", "ERROR"))
                        .defaultValue("INFO")
                        .description("Log level of the message.")
                        .build());
    }

    @Override
    protected void doExecute(TaskParameters parameters, Heartbeat heartbeat) {
        heartbeat.beat();
        Level level = parameters.getEnum(LEVEL, Level.class);
        log.atLevel(level).log("[LOG task] {}", parameters.getString(MESSAGE));
        heartbeat.beat();
    }
}
