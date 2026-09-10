package com.github.stimur1709.cloudops.probe.ping;

import com.github.stimur1709.cloudops.probe.ProbeErrorCode;

record PingOutcome(boolean completed, PingResult result, ProbeErrorCode errorCode, String errorMessage) {

    static PingOutcome completed(PingResult result) {
        return new PingOutcome(true, result, null, null);
    }

    static PingOutcome failed(ProbeErrorCode code, String message) {
        return new PingOutcome(false, null, code, message);
    }
}
