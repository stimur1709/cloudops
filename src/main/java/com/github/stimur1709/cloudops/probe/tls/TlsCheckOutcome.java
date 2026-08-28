package com.github.stimur1709.cloudops.probe.tls;

import com.github.stimur1709.cloudops.probe.ProbeErrorCode;

record TlsCheckOutcome(boolean completed, TlsCheckResult result, ProbeErrorCode errorCode, String errorMessage) {

    static TlsCheckOutcome completed(TlsCheckResult result) {
        return new TlsCheckOutcome(true, result, null, null);
    }

    static TlsCheckOutcome failed(ProbeErrorCode code, String message) {
        return new TlsCheckOutcome(false, null, code, message);
    }
}
