package com.github.stimur1709.cloudops.monitoring.execution;

import com.github.stimur1709.cloudops.monitoring.StorageMode;
import com.github.stimur1709.cloudops.probe.ProbeType;
import com.github.stimur1709.cloudops.resource.config.ResourceConfig;

public record MonitorExecutionContext(
        long monitorId,
        long resourceId,
        ProbeType type,
        StorageMode storageMode,
        ResourceConfig resourceConfig
) {
}
