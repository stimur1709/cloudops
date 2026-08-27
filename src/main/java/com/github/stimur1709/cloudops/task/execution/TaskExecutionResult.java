package com.github.stimur1709.cloudops.task.execution;

import com.github.stimur1709.cloudops.task.TaskErrorCode;

public sealed interface TaskExecutionResult {

    record Completed(Object result) implements TaskExecutionResult {
    }

    record Failed(TaskErrorCode errorCode, String errorMessage) implements TaskExecutionResult {
    }

    static Completed completed(Object result) {
        return new Completed(result);
    }

    static Failed failed(TaskErrorCode errorCode, String errorMessage) {
        return new Failed(errorCode, errorMessage);
    }
}
