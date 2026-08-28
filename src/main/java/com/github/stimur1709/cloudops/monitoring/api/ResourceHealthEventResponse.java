package com.github.stimur1709.cloudops.monitoring.api;

import java.time.Instant;

import com.github.stimur1709.cloudops.monitoring.ResourceHealthStatus;
import com.github.stimur1709.cloudops.monitoring.persistence.ResourceHealthEventEntity;

public record ResourceHealthEventResponse(
        long id,
        ResourceHealthStatus fromStatus,
        ResourceHealthStatus toStatus,
        Instant changedAt
) {

    public static ResourceHealthEventResponse from(ResourceHealthEventEntity event) {
        return new ResourceHealthEventResponse(
                event.id(), event.fromStatus(), event.toStatus(), event.changedAt()
        );
    }
}
