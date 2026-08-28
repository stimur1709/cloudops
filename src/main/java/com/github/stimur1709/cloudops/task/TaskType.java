package com.github.stimur1709.cloudops.task;

import com.github.stimur1709.cloudops.probe.ProbeType;

public enum TaskType {
    HTTP_CHECK(ProbeType.HTTP_CHECK),
    PORT_CHECK(ProbeType.PORT_CHECK),
    DNS_CHECK(ProbeType.DNS_CHECK),
    PING(ProbeType.PING),
    TLS_CHECK(ProbeType.TLS_CHECK);

    private final ProbeType probeType;

    TaskType(ProbeType probeType) {
        this.probeType = probeType;
    }

    public ProbeType probeType() {
        return probeType;
    }
}
