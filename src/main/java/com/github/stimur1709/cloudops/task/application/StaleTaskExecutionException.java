package com.github.stimur1709.cloudops.task.application;

import java.util.UUID;

public class StaleTaskExecutionException extends RuntimeException {

    public StaleTaskExecutionException(long taskId, UUID executionId) {
        super("Task %d execution %s is stale".formatted(taskId, executionId));
    }
}
