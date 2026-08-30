package com.github.stimur1709.cloudops.task.execution;

import com.github.stimur1709.cloudops.task.TaskType;

public class TaskHandlerNotFoundException extends RuntimeException {

    public TaskHandlerNotFoundException(TaskType type) {
        super("No task handler is configured for " + type);
    }
}
