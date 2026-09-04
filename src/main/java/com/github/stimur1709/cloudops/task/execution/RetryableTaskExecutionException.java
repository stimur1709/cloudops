package com.github.stimur1709.cloudops.task.execution;

import com.github.stimur1709.cloudops.task.TaskErrorCode;

/** Marks a temporary internal handler failure that can succeed when invoked again. */
public final class RetryableTaskExecutionException extends RuntimeException {

    private final TaskErrorCode errorCode;
    private final String safeMessage;

    public RetryableTaskExecutionException(String message, Throwable cause) {
        this(TaskErrorCode.RETRY_EXHAUSTED, message, cause);
    }

    public RetryableTaskExecutionException(String message) {
        this(TaskErrorCode.RETRY_EXHAUSTED, message, null);
    }

    public RetryableTaskExecutionException(TaskErrorCode errorCode, String safeMessage, Throwable cause) {
        super(safeMessage, cause);
        this.errorCode = errorCode;
        this.safeMessage = safeMessage;
    }

    public TaskErrorCode errorCode() {
        return errorCode;
    }

    public String safeMessage() {
        return safeMessage;
    }
}
