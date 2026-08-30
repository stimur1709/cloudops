package com.github.stimur1709.cloudops.task.execution;

import com.github.stimur1709.cloudops.task.TaskErrorCode;

public sealed interface TaskExecutionResult {

    record Completed(Object data) implements TaskExecutionResult {
    }

    record Failed(TaskErrorCode errorCode, String message) implements TaskExecutionResult {
    }

    static TaskExecutionResult completed(Object data) {
        return new Completed(data);
    }

    static TaskExecutionResult failed(TaskErrorCode errorCode, String message) {
        return new Failed(errorCode, message);
    }
}
