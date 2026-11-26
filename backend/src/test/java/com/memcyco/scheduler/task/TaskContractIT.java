package com.memcyco.scheduler.task;

import com.memcyco.scheduler.support.IntegrationTest;
import org.junit.jupiter.api.DynamicContainer;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

/**
 * The contract every task must honour, checked for <b>every Task bean in the application</b>: a newly added task is
 * covered automatically, without writing a test for it.
 * <ul>
 *     <li>its definition is valid (key format, name, parameter schema)</li>
 *     <li>it runs with values that satisfy its schema, and it <b>sends heartbeats</b> while doing so</li>
 *     <li>it refuses values that break its schema (missing required parameters)</li>
 * </ul>
 */
class TaskContractIT extends IntegrationTest {

    @Autowired
    private List<Task> tasks;

    @TestFactory
    Stream<DynamicContainer> everyTaskHonoursTheTaskContract() {
        assertThat(tasks).extracting(Task::key).contains("LOG", "DUMMY_EMAIL", "DB_QUERY");
        return tasks.stream().map(task -> DynamicContainer.dynamicContainer(task.key(), Stream.of(
                dynamicTest("has a valid definition", () -> {
                    assertThat(TaskRegistry.validateDefinition(task)).isEmpty();
                    assertThat(task.name()).isNotBlank();
                    assertThat(task.description()).isNotBlank();
                }),
                dynamicTest("sends heartbeats while it runs", () -> {
                    AtomicInteger beats = new AtomicInteger();
                    task.execute(validParameters(task), beats::incrementAndGet);
                    assertThat(beats.get()).as("heartbeats sent by %s", task.key()).isPositive();
                }),
                requiredParametersTest(task))));
    }

    private static DynamicTest requiredParametersTest(Task task) {
        boolean hasRequired = task.parameters().stream().anyMatch(p -> p.required() && p.defaultValue() == null);
        return dynamicTest("refuses to run without its required parameters", () -> {
            if (hasRequired) {
                assertThatThrownBy(() -> task.execute(Map.of(), () -> { }))
                        .isInstanceOf(InvalidTaskParametersException.class);
            } else {
                task.execute(Map.of(), () -> { });
            }
        });
    }

    /** A value for every parameter, derived from the schema only. */
    static Map<String, Object> validParameters(Task task) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (ParameterDefinition p : task.parameters()) {
            values.put(p.name(), switch (p.type()) {
                case STRING, TEXT -> "Task contract test";
                case EMAIL -> "contract-test@example.com";
                case BOOLEAN -> true;
                case ENUM -> p.allowedValues().getFirst();
                case NUMBER -> p.min() != null ? (Object) p.min().longValue()
                        : p.defaultValue() != null ? p.defaultValue() : 1L;
            });
        }
        return values;
    }
}
