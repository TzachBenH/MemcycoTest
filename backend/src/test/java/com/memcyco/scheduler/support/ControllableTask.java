package com.memcyco.scheduler.support;

import com.memcyco.scheduler.task.ExecutionNoLongerRunningException;
import com.memcyco.scheduler.task.Heartbeat;
import com.memcyco.scheduler.task.NonRetryableTaskException;
import com.memcyco.scheduler.task.ParameterDefinition;
import com.memcyco.scheduler.task.ParameterType;
import com.memcyco.scheduler.task.Task;
import com.memcyco.scheduler.task.TaskParameters;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Test-only task whose behaviour each integration test chooses through its parameters, so the real execution engine
 * (Quartz, runtime lock, retries, heartbeat) can be driven into every path. Registered only in the test context.
 * <p>
 * Parameters: {@code scenario} (what to do) and an optional {@code label} (to vary the parameters, i.e. the run key
 * and the fingerprint, and to count invocations separately).
 */
public class ControllableTask extends Task {

    public static final String KEY = "TEST_CONTROLLABLE";
    static final String SCENARIO = "scenario";
    static final String LABEL = "label";
    /** Safety net: a blocked run gives up after this long if a test forgets to release it. */
    private static final Duration MAX_BLOCK = Duration.ofSeconds(30);

    public enum Scenario {
        /** Beats and succeeds. Listed first, so the task contract test runs this one. */
        SUCCEED,
        /** Always throws a retryable exception. */
        FAIL,
        /** Fails the first time it runs for a label, then succeeds. */
        FAIL_FIRST_ATTEMPT,
        /** Throws {@link NonRetryableTaskException}. */
        FAIL_PERMANENTLY,
        /** Beats every 50 ms until {@link #releaseAll()} is called. */
        BLOCK
    }

    private final Map<String, AtomicInteger> invocations = new ConcurrentHashMap<>();
    private final BlockingQueue<String> started = new LinkedBlockingQueue<>();
    private final AtomicInteger stoppedByHeartbeat = new AtomicInteger();
    private volatile CountDownLatch release = new CountDownLatch(1);

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public String name() {
        return "Test: controllable task";
    }

    @Override
    public String description() {
        return "Test-only task: succeeds, fails or blocks as the test asks.";
    }

    @Override
    public List<ParameterDefinition> parameters() {
        return List.of(
                ParameterDefinition.builder(SCENARIO, ParameterType.ENUM)
                        .required()
                        .allowedValues(Arrays.stream(Scenario.values()).map(Enum::name).toList())
                        .build(),
                ParameterDefinition.builder(LABEL, ParameterType.STRING).build());
    }

    @Override
    protected void doExecute(TaskParameters parameters, Heartbeat heartbeat) throws Exception {
        heartbeat.beat();
        Scenario scenario = parameters.getEnum(SCENARIO, Scenario.class);
        String label = parameters.getOptionalString(LABEL).orElse("");
        int invocation = invocations.computeIfAbsent(label, k -> new AtomicInteger()).incrementAndGet();
        switch (scenario) {
            case SUCCEED -> log.info("Controllable task '{}' succeeded", label);
            case FAIL -> throw new IllegalStateException("Simulated failure #" + invocation);
            case FAIL_FIRST_ATTEMPT -> {
                if (invocation == 1) {
                    throw new IllegalStateException("Simulated failure of the first attempt");
                }
            }
            case FAIL_PERMANENTLY -> throw new NonRetryableTaskException("Simulated permanent failure");
            case BLOCK -> block(label, heartbeat);
        }
        heartbeat.beat();
    }

    private void block(String label, Heartbeat heartbeat) throws InterruptedException {
        CountDownLatch latch = release;
        started.add(label);
        long deadline = System.nanoTime() + MAX_BLOCK.toNanos();
        try {
            while (!latch.await(50, TimeUnit.MILLISECONDS)) {
                heartbeat.beat();
                if (System.nanoTime() > deadline) {
                    throw new IllegalStateException("Blocked for " + MAX_BLOCK + "; the test never released it");
                }
            }
        } catch (ExecutionNoLongerRunningException e) {
            stoppedByHeartbeat.incrementAndGet();
            throw e;
        }
    }

    // ---- test controls ----

    /** Waits until a BLOCK run has started and returns its label. */
    public String awaitStarted(Duration timeout) throws InterruptedException {
        String label = started.poll(timeout.toMillis(), TimeUnit.MILLISECONDS);
        if (label == null) {
            throw new AssertionError("No blocking run started within " + timeout);
        }
        return label;
    }

    /** Lets every currently blocked run finish successfully. */
    public void releaseAll() {
        release.countDown();
    }

    public int invocations(String label) {
        AtomicInteger count = invocations.get(label);
        return count == null ? 0 : count.get();
    }

    /** How many runs stopped because their heartbeat found them no longer RUNNING. */
    public int stoppedByHeartbeat() {
        return stoppedByHeartbeat.get();
    }

    /** Releases blocked runs and forgets all counters; called before each test. */
    public void reset() {
        release.countDown();
        release = new CountDownLatch(1);
        invocations.clear();
        started.clear();
        stoppedByHeartbeat.set(0);
    }
}
