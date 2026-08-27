package com.github.stimur1709.cloudops.probe.execution;

import com.github.stimur1709.cloudops.probe.ProbeType;
import com.github.stimur1709.cloudops.resource.config.ResourceConfig;

public record ProbeExecutionContext(long resourceId, ProbeType type, ResourceConfig resourceConfig) {
}
