package com.memcyco.scheduler.task;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Behavior of the Template Method in the base {@link Task} class. */
class TaskTest {

    private static final ParameterDefinition MESSAGE = ParameterDefinition.builder("message", ParameterType.STRING)
            .required().build();
    private static final ParameterDefinition LEVEL = ParameterDefinition.builder("level", ParameterType.ENUM)
            .allowedValues(List.of("INFO", "WARN")).defaultValue("INFO").build();

    @Test
    void invalidParametersAreRejectedBeforeTheTaskRuns() {
        AtomicInteger runs = new AtomicInteger();
        Task task = TestTasks.task("T", "T", List.of(MESSAGE), (p, hb) -> runs.incrementAndGet());

        assertThatThrownBy(() -> task.execute(Map.of(), () -> { }))
                .isInstanceOf(InvalidTaskParametersException.class)
                .isInstanceOf(NonRetryableTaskException.class)
                .hasMessageContaining("parameters.message is required");
        assertThat(runs).hasValue(0);
    }

    @Test
    void taskReceivesNormalizedParametersWithDefaults() throws Exception {
        AtomicReference<TaskParameters> received = new AtomicReference<>();
        Task task = TestTasks.task("T", "T", List.of(MESSAGE, LEVEL), (p, hb) -> {
            hb.beat();
            received.set(p);
        });

        task.execute(Map.of("message", "  hi "), () -> { });

        assertThat(received.get().asMap()).containsEntry("message", "hi").containsEntry("level", "INFO");
    }

    @Test
    void heartbeatsAreForwardedToTheRealHeartbeat() throws Exception {
        AtomicInteger beats = new AtomicInteger();
        Task task = TestTasks.task("T", "T", List.of(), (p, hb) -> {
            hb.beat();
            hb.beat();
        });

        task.execute(Map.of(), beats::incrementAndGet);

        assertThat(beats).hasValue(2);
    }

    @Test
    void exceptionsFromTheTaskPropagateToTheEngine() {
        Task task = TestTasks.task("T", "T", List.of(), (p, hb) -> {
            throw new IllegalStateException("boom");
        });

        assertThatThrownBy(() -> task.execute(Map.of(), () -> { })).hasMessage("boom");
    }
}
