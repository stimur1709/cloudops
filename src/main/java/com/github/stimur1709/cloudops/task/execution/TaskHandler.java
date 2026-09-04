package com.github.stimur1709.cloudops.task.execution;

import com.github.stimur1709.cloudops.resource.config.ResourceConfig;
import com.github.stimur1709.cloudops.task.TaskType;

public interface TaskHandler {

    TaskType type();

    default void validateCreation(long resourceId, ResourceConfig resourceConfig) {}

    TaskExecutionResult execute(TaskExecutionContext context);
}
