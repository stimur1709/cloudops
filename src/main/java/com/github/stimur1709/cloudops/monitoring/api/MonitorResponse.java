package com.github.stimur1709.cloudops.monitoring.api;

import java.time.Instant;

import com.github.stimur1709.cloudops.monitoring.HealthStatus;
import com.github.stimur1709.cloudops.monitoring.StorageMode;
import com.github.stimur1709.cloudops.monitoring.persistence.MonitorEntity;
import com.github.stimur1709.cloudops.probe.ProbeType;
import tools.jackson.databind.JsonNode;

public record MonitorResponse(
        long id,
        long resourceId,
        ProbeType type,
        boolean enabled,
        int intervalSeconds,
        Instant nextRunAt,
        StorageMode storageMode,
        Integer retentionDays,
        Instant lastCheckedAt,
        JsonNode lastResult,
        HealthStatus healthStatus,
        int failureThreshold,
        int recoveryThreshold
) {
    public static MonitorResponse from(MonitorEntity monitor) {
        return new MonitorResponse(
                monitor.id(), monitor.resourceId(), monitor.type(), monitor.enabled(), monitor.intervalSeconds(),
                monitor.nextRunAt(), monitor.storageMode(), monitor.retentionDays(), monitor.lastCheckedAt(),
                monitor.lastResult(), monitor.healthStatus(), monitor.failureThreshold(), monitor.recoveryThreshold()
        );
    }
}
