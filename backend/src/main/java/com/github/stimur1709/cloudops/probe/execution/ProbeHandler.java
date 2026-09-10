package com.github.stimur1709.cloudops.probe.execution;

import com.github.stimur1709.cloudops.probe.ProbeType;
import com.github.stimur1709.cloudops.resource.config.ResourceConfig;

public interface ProbeHandler {

    ProbeType type();

    boolean isCompatibleWith(ResourceConfig resourceConfig);

    ProbeExecutionResult execute(ProbeExecutionContext context);
}
