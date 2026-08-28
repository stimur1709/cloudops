package com.github.stimur1709.cloudops.resource.api;

import java.time.Instant;

import com.github.stimur1709.cloudops.resource.ResourceStatus;
import com.github.stimur1709.cloudops.resource.ResourceType;
import com.github.stimur1709.cloudops.monitoring.ResourceHealthStatus;
import com.github.stimur1709.cloudops.resource.application.ResourceDetails;
import com.github.stimur1709.cloudops.resource.config.ResourceConfig;
import com.github.stimur1709.cloudops.resource.config.ResourceConfigMapper;
import com.github.stimur1709.cloudops.resource.persistence.ResourceEntity;

public record ResourceResponse(
        Long id,
        String name,
        ResourceType type,
        ResourceStatus status,
        ResourceHealthStatus healthStatus,
        Long organizationId,
        ResourceConfig config,
        Instant createdAt,
        Instant updatedAt
) {

    static ResourceResponse from(ResourceDetails details, ResourceConfigMapper configMapper) {
        ResourceEntity resource = details.resource();
        return new ResourceResponse(
                resource.id(),
                resource.name(),
                resource.type(),
                resource.status(),
                details.healthStatus(),
                resource.organizationId(),
                configMapper.fromJson(resource.type(), resource.config()),
                resource.createdAt(),
                resource.updatedAt()
        );
    }
}
