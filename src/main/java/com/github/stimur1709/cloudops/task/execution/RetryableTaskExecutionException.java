package com.github.stimur1709.cloudops.task.execution;

/** Marks a temporary internal handler failure that can succeed when invoked again. */
public final class RetryableTaskExecutionException extends RuntimeException {

    public RetryableTaskExecutionException(String message, Throwable cause) {
        super(message, cause);
    }

    public RetryableTaskExecutionException(String message) {
        super(message);
    }
}
