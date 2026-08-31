package com.github.stimur1709.cloudops.monitoring.api;

import com.github.stimur1709.cloudops.monitoring.persistence.MonitoringResultEntity;
import java.time.Instant;
import tools.jackson.databind.JsonNode;

public record MonitoringResultResponse(long id, long monitorId, Instant checkedAt, JsonNode result) {

    public static MonitoringResultResponse from(MonitoringResultEntity result) {
        return new MonitoringResultResponse(result.id(), result.monitorId(), result.checkedAt(), result.result());
    }
}
