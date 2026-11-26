package com.memcyco.scheduler.task;

/** No task with the given key exists in the code (e.g. it was removed while schedulings still reference it). */
public class UnknownTaskException extends NonRetryableTaskException {

    public UnknownTaskException(String taskKey) {
        super("Unknown task: " + taskKey);
    }
}
