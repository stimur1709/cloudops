package com.github.stimur1709.cloudops.task.application;

import com.github.stimur1709.cloudops.task.TaskType;

public interface TaskHandler {

    TaskType supports();

    TaskExecutionResult execute(TaskExecutionContext context);
}
