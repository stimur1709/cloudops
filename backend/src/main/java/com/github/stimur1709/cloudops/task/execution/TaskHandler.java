package com.github.stimur1709.cloudops.task.execution;

import com.github.stimur1709.cloudops.task.TaskType;

public interface TaskHandler {

    TaskType type();

    TaskExecutionResult execute(TaskExecutionContext context);
}
