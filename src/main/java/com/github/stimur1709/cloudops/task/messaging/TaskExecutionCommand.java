package com.github.stimur1709.cloudops.task.messaging;

public record TaskExecutionCommand(long taskId) {
    public TaskExecutionCommand {
        if (taskId <= 0) {
            throw new IllegalArgumentException("taskId must be positive");
        }
    }
}
