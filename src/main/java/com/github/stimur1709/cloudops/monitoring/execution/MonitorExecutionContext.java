package com.github.stimur1709.cloudops.monitoring.execution;

import com.github.stimur1709.cloudops.probe.ProbeType;
import com.github.stimur1709.cloudops.resource.config.ResourceConfig;
import com.github.stimur1709.cloudops.monitoring.settings.EffectiveProbeSettings;

public record MonitorExecutionContext(
        long monitorId,
        long resourceId,
        ProbeType type,
        ResourceConfig resourceConfig,
        EffectiveProbeSettings settings
) {
}
