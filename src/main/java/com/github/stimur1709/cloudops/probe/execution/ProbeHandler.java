package com.github.stimur1709.cloudops.probe.execution;

import com.github.stimur1709.cloudops.probe.ProbeType;
import com.github.stimur1709.cloudops.resource.config.ResourceConfig;

public interface ProbeHandler {

    ProbeType supports();

    void validate(ResourceConfig config);

    ProbeExecutionResult execute(ProbeExecutionContext context);
}
