package com.memcyco.scheduler.task;

import java.util.List;

/** Small configurable task for unit tests of the task framework. */
final class TestTasks {

    private TestTasks() {
    }

    static Task task(String key, String name, List<ParameterDefinition> parameters) {
        return task(key, name, parameters, (p, hb) -> hb.beat());
    }

    static Task task(String key, String name, List<ParameterDefinition> parameters, Body body) {
        return new Task() {
            @Override
            public String key() {
                return key;
            }

            @Override
            public String name() {
                return name;
            }

            @Override
            public String description() {
                return "test task";
            }

            @Override
            public List<ParameterDefinition> parameters() {
                return parameters;
            }

            @Override
            protected void doExecute(TaskParameters parameters, Heartbeat heartbeat) throws Exception {
                body.run(parameters, heartbeat);
            }
        };
    }

    @FunctionalInterface
    interface Body {
        void run(TaskParameters parameters, Heartbeat heartbeat) throws Exception;
    }
}
