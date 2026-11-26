package com.memcyco.scheduler.task.impl;

import com.memcyco.scheduler.task.Heartbeat;
import com.memcyco.scheduler.task.ParameterDefinition;
import com.memcyco.scheduler.task.ParameterType;
import com.memcyco.scheduler.task.Task;
import com.memcyco.scheduler.task.TaskParameters;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * Simulates sending an email: "connects" to a fake SMTP server, "sends", and logs the result. Nothing is sent.
 * <p>
 * Demo aid: recipients at {@value #FAILING_DOMAIN} simulate an SMTP outage (the task throws), which makes the
 * retry mechanism visible in the history.
 */
@Component
public class DummyEmailTask extends Task {

    public static final String KEY = "DUMMY_EMAIL";
    public static final String FAILING_DOMAIN = "fail.example";
    static final String TO = "to";
    static final String SUBJECT = "subject";
    static final String BODY = "body";
    private static final Duration SIMULATED_NETWORK_DELAY = Duration.ofMillis(200);

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public String name() {
        return "Send Email (simulated)";
    }

    @Override
    public String description() {
        return "Simulates sending an email and logs it. No real email is sent. "
                + "Addresses @" + FAILING_DOMAIN + " simulate an SMTP failure.";
    }

    @Override
    public List<ParameterDefinition> parameters() {
        return List.of(
                ParameterDefinition.builder(TO, ParameterType.EMAIL)
                        .label("To")
                        .required()
                        .description("Recipient email address.")
                        .build(),
                ParameterDefinition.builder(SUBJECT, ParameterType.STRING)
                        .label("Subject")
                        .required()
                        .build(),
                ParameterDefinition.builder(BODY, ParameterType.TEXT)
                        .label("Body")
                        .description("Optional message body.")
                        .build());
    }

    @Override
    protected void doExecute(TaskParameters parameters, Heartbeat heartbeat) throws InterruptedException {
        heartbeat.beat();
        String to = parameters.getString(TO);

        log.info("Connecting to simulated SMTP server...");
        Thread.sleep(SIMULATED_NETWORK_DELAY);
        heartbeat.beat();
        if (to.toLowerCase().endsWith("@" + FAILING_DOMAIN)) {
            throw new IllegalStateException("Simulated SMTP failure: connection refused for " + to);
        }

        Thread.sleep(SIMULATED_NETWORK_DELAY);
        int bodyLength = parameters.getOptionalString(BODY).map(String::length).orElse(0);
        log.info("[DUMMY_EMAIL task] Email sent | to={} | subject={} | body={} chars",
                to, parameters.getString(SUBJECT), bodyLength);
        heartbeat.beat();
    }
}
