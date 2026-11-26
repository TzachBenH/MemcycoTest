package com.memcyco.scheduler.task;

import com.memcyco.scheduler.validation.ParameterValidationResult;
import com.memcyco.scheduler.validation.ParameterValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * A predefined, read-only task that schedulings can run.
 * <p>
 * Tasks are defined in code: to add one, create a subclass annotated with {@code @Component}. It is picked up by
 * {@link TaskRegistry}, exposed by {@code GET /api/tasks}, rendered in the UI from {@link #parameters()}, and
 * covered automatically by the task contract test.
 * <p>
 * Template Method: {@link #execute} is final and runs the same steps for every task (validate parameters → run the
 * subclass's {@link #doExecute} → check that heartbeats were sent). Subclasses implement only {@code doExecute}.
 */
public abstract class Task {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    /** Stable identifier stored in {@code schedulings.task_key}, e.g. "LOG". Upper-case letters and underscores. */
    public abstract String key();

    /** Human-readable name, e.g. "Log Message". */
    public abstract String name();

    public abstract String description();

    /** Parameter schema; empty if the task takes no parameters. */
    public abstract List<ParameterDefinition> parameters();

    /**
     * Runs the task. Called by the execution engine; never override.
     *
     * @param rawParameters parameter values as stored on the scheduling
     * @param heartbeat     liveness signal; passed on to {@link #doExecute}
     * @throws InvalidTaskParametersException if the values no longer match the schema (not retried)
     * @throws Exception                      any failure of the task itself (retried by the engine)
     */
    public final void execute(Map<String, Object> rawParameters, Heartbeat heartbeat) throws Exception {
        ParameterValidationResult validation = ParameterValidator.validate(parameters(), rawParameters);
        if (!validation.isValid()) {
            throw new InvalidTaskParametersException(key(), validation.errors());
        }
        CountingHeartbeat countingHeartbeat = new CountingHeartbeat(heartbeat);
        log.debug("Starting task {}", key());
        doExecute(new TaskParameters(validation.parameters()), countingHeartbeat);
        if (countingHeartbeat.count() == 0) {
            log.warn("Task {} finished without sending any heartbeat; every task must call heartbeat.beat()",
                    key());
        }
        log.debug("Finished task {}", key());
    }

    /**
     * The task's own work.
     * <p>
     * <b>Implementations MUST call {@code heartbeat.beat()}</b>: at the start, between steps, and inside long
     * loops, so that no more than a few minutes pass between beats (the stuck timeout is 5 minutes). Blocking calls
     * must use timeouts shorter than that. Throw {@link NonRetryableTaskException} for failures that a retry
     * cannot fix; any other exception is retried by the engine.
     */
    protected abstract void doExecute(TaskParameters parameters, Heartbeat heartbeat) throws Exception;
}
