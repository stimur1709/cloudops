package com.github.stimur1709.cloudops.probe.execution;

import com.github.stimur1709.cloudops.probe.ProbeType;

public interface ProbeHandler {

    ProbeType supports();

    ProbeExecutionResult execute(ProbeExecutionContext context);
}
