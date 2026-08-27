package com.github.stimur1709.cloudops.task.execution;

import com.github.stimur1709.cloudops.task.TaskType;

public final class TaskHandlerNotFoundException extends RuntimeException {

    public TaskHandlerNotFoundException(TaskType type) {
        super("No TaskHandler is registered for task type " + type);
    }
}
