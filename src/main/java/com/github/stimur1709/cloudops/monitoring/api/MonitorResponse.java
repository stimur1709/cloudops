package com.github.stimur1709.cloudops.monitoring.api;

import java.time.Instant;

import com.github.stimur1709.cloudops.monitoring.HealthStatus;
import com.github.stimur1709.cloudops.monitoring.persistence.MonitorEntity;
import com.github.stimur1709.cloudops.probe.ProbeType;
import tools.jackson.databind.JsonNode;

public record MonitorResponse(
        long id,
        long resourceId,
        ProbeType type,
        Instant nextRunAt,
        Instant lastCheckedAt,
        JsonNode lastResult,
        HealthStatus healthStatus
) {
    public static MonitorResponse from(MonitorEntity monitor) {
        return new MonitorResponse(
                monitor.id(),
                monitor.resourceId(),
                monitor.type(),
                monitor.nextRunAt(),
                monitor.lastCheckedAt(),
                monitor.lastResult(),
                monitor.healthStatus()
        );
    }
}
