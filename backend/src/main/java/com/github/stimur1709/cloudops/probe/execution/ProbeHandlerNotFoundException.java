package com.github.stimur1709.cloudops.probe.execution;

import com.github.stimur1709.cloudops.probe.ProbeType;

public final class ProbeHandlerNotFoundException extends RuntimeException {

    public ProbeHandlerNotFoundException(ProbeType type) {
        super("No ProbeHandler is registered for probe type " + type);
    }
}
