package com.memcyco.scheduler.controller;

import com.memcyco.scheduler.support.IntegrationTest;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItems;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The predefined tasks and their parameter schemas, as the UI receives them. */
class TaskApiIT extends IntegrationTest {

    @Test
    void listsThePredefinedTasksWithTheirParameterSchemas() throws Exception {
        mvc.perform(get("/api/tasks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].key").value(hasItems("LOG", "DUMMY_EMAIL", "DB_QUERY")))
                // LOG: a required message and an optional level with a default
                .andExpect(jsonPath("$[?(@.key == 'LOG')].name").value(contains("Log Message")))
                .andExpect(jsonPath("$[?(@.key == 'LOG')].parameters[0].name").value(contains("message")))
                .andExpect(jsonPath("$[?(@.key == 'LOG')].parameters[0].type").value(contains("STRING")))
                .andExpect(jsonPath("$[?(@.key == 'LOG')].parameters[0].required").value(contains(true)))
                .andExpect(jsonPath("$[?(@.key == 'LOG')].parameters[1].type").value(contains("ENUM")))
                .andExpect(jsonPath("$[?(@.key == 'LOG')].parameters[1].defaultValue").value(contains("INFO")))
                .andExpect(jsonPath("$[?(@.key == 'LOG')].parameters[1].allowedValues[*]")
                        .value(contains("INFO", "WARN", "ERROR")))
                // DUMMY_EMAIL: the recipient is an EMAIL field
                .andExpect(jsonPath("$[?(@.key == 'DUMMY_EMAIL')].parameters[0].type").value(contains("EMAIL")))
                // DB_QUERY: only predefined reports; a bounded time window
                .andExpect(jsonPath("$[?(@.key == 'DB_QUERY')].parameters[0].allowedValues[*]")
                        .value(contains("SCHEDULINGS_BY_STATUS", "SCHEDULINGS_BY_TASK", "EXECUTIONS_BY_STATUS")))
                .andExpect(jsonPath("$[?(@.key == 'DB_QUERY')].parameters[1].min").value(contains(1.0)))
                .andExpect(jsonPath("$[?(@.key == 'DB_QUERY')].parameters[1].max").value(contains(720.0)));
    }
}
