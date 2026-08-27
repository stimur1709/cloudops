package com.github.stimur1709.cloudops.task.httpcheck;

import com.github.stimur1709.cloudops.task.TaskErrorCode;

public record HttpCheckOutcome(HttpCheckResult result, TaskErrorCode errorCode, String errorMessage) {

    public static HttpCheckOutcome completed(HttpCheckResult result) {
        return new HttpCheckOutcome(result, null, null);
    }

    public static HttpCheckOutcome failed(TaskErrorCode errorCode, String errorMessage) {
        return new HttpCheckOutcome(null, errorCode, errorMessage);
    }

    public boolean successfulCall() {
        return result != null;
    }
}
