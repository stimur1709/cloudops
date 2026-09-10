package com.github.stimur1709.cloudops.monitoring.api;

import com.github.stimur1709.cloudops.monitoring.HealthStatus;
import com.github.stimur1709.cloudops.monitoring.persistence.MonitorEntity;
import com.github.stimur1709.cloudops.probe.ProbeType;
import com.github.stimur1709.cloudops.probe.execution.ProbeExecutionResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import tools.jackson.databind.JsonNode;

public record MonitorResponse(
        long id,
        long resourceId,
        ProbeType type,
        @Schema(nullable = true) Instant nextRunAt,
        @Schema(nullable = true) Instant lastCheckedAt,

        @Schema(implementation = ProbeExecutionResult.class, nullable = true)
        JsonNode lastResult,

        HealthStatus healthStatus) {
    public static MonitorResponse from(MonitorEntity monitor) {
        return new MonitorResponse(
                monitor.id(),
                monitor.resourceId(),
                monitor.type(),
                monitor.nextRunAt(),
                monitor.lastCheckedAt(),
                monitor.lastResult(),
                monitor.healthStatus());
    }
}
