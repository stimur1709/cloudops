package com.github.stimur1709.cloudops.probe.ssh;

import com.github.stimur1709.cloudops.probe.ProbeErrorCode;

record SshCheckOutcome(boolean completed, SshCheckResult result, ProbeErrorCode errorCode, String errorMessage) {

    static SshCheckOutcome completed(SshCheckResult result) {
        return new SshCheckOutcome(true, result, null, null);
    }

    static SshCheckOutcome failed(ProbeErrorCode errorCode, String errorMessage) {
        return new SshCheckOutcome(false, null, errorCode, errorMessage);
    }
}
