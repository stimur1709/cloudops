package com.github.stimur1709.cloudops.resource.application;

import com.github.stimur1709.cloudops.monitoring.ResourceHealthStatus;
import com.github.stimur1709.cloudops.resource.persistence.ResourceEntity;

public record ResourceDetails(
        ResourceEntity resource,
        ResourceHealthStatus healthStatus
) {
}
