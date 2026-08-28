package com.github.stimur1709.cloudops.probe.port;

import com.github.stimur1709.cloudops.probe.ProbeErrorCode;

record PortCheckOutcome(
        boolean completed,
        PortCheckResult result,
        ProbeErrorCode errorCode,
        String errorMessage
) {

    static PortCheckOutcome completed(PortCheckResult result) {
        return new PortCheckOutcome(true, result, null, null);
    }

    static PortCheckOutcome failed(ProbeErrorCode errorCode, String errorMessage) {
        return new PortCheckOutcome(false, null, errorCode, errorMessage);
    }
}
