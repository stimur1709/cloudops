package com.github.stimur1709.cloudops.probe.http;

import com.github.stimur1709.cloudops.probe.ProbeErrorCode;

public record HttpCheckOutcome(HttpCheckResult result, ProbeErrorCode errorCode, String errorMessage) {

    public static HttpCheckOutcome completed(HttpCheckResult result) {
        return new HttpCheckOutcome(result, null, null);
    }

    public static HttpCheckOutcome failed(ProbeErrorCode errorCode, String errorMessage) {
        return new HttpCheckOutcome(null, errorCode, errorMessage);
    }

    public boolean completed() {
        return result != null;
    }
}
