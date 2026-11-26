package com.memcyco.scheduler.task;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TaskRegistryTest {

    @Test
    void findsTasksByKeyAndListsThemByName() {
        Task b = TestTasks.task("B_TASK", "Beta", List.of());
        Task a = TestTasks.task("A_TASK", "Alpha", List.of());

        TaskRegistry registry = new TaskRegistry(List.of(b, a));

        assertThat(registry.all()).extracting(Task::name).containsExactly("Alpha", "Beta");
        assertThat(registry.find("B_TASK")).containsSame(b);
        assertThat(registry.find("MISSING")).isEmpty();
        assertThat(registry.find(null)).isEmpty();
    }

    @Test
    void getUnknownKeyThrowsNonRetryableException() {
        TaskRegistry registry = new TaskRegistry(List.of());

        assertThatThrownBy(() -> registry.get("GONE"))
                .isInstanceOf(UnknownTaskException.class)
                .isInstanceOf(NonRetryableTaskException.class)
                .hasMessage("Unknown task: GONE");
    }

    @Test
    void duplicateKeysStopTheApplication() {
        assertThatThrownBy(() -> new TaskRegistry(List.of(
                TestTasks.task("SAME", "One", List.of()),
                TestTasks.task("SAME", "Two", List.of()))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Duplicate task key 'SAME'");
    }

    @Test
    void invalidKeyFormatIsRejected() {
        assertThatThrownBy(() -> new TaskRegistry(List.of(TestTasks.task("bad-key", "Bad", List.of()))))
                .hasMessageContaining("key must match");
    }

    @Test
    void invalidParameterDefinitionsAreRejected() {
        var enumWithoutValues = ParameterDefinition.builder("level", ParameterType.ENUM).build();
        var minOnString = ParameterDefinition.builder("text", ParameterType.STRING).min(1).build();
        var badDefault = ParameterDefinition.builder("count", ParameterType.NUMBER).max(5).defaultValue(10).build();
        var duplicate = ParameterDefinition.builder("text", ParameterType.TEXT).build();

        assertThatThrownBy(() -> new TaskRegistry(List.of(
                TestTasks.task("BROKEN", "Broken", List.of(enumWithoutValues, minOnString, badDefault, duplicate)))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("'level': ENUM needs allowedValues")
                .hasMessageContaining("'text': min/max is only for NUMBER")
                .hasMessageContaining("'count': default value [must be at most 5]")
                .hasMessageContaining("'text': duplicate name");
    }
}
