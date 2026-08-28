package com.github.stimur1709.cloudops.probe.dns;

import com.github.stimur1709.cloudops.probe.ProbeErrorCode;

record DnsCheckOutcome(boolean completed, DnsCheckResult result, ProbeErrorCode errorCode, String errorMessage) {

    static DnsCheckOutcome completed(DnsCheckResult result) {
        return new DnsCheckOutcome(true, result, null, null);
    }

    static DnsCheckOutcome failed(String message) {
        return new DnsCheckOutcome(false, null, ProbeErrorCode.DNS_ERROR, message);
    }
}
