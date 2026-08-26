package com.github.stimur1709.cloudops.resource.api;

import java.time.Instant;

import com.github.stimur1709.cloudops.resource.ResourceStatus;
import com.github.stimur1709.cloudops.resource.ResourceType;
import com.github.stimur1709.cloudops.resource.config.ResourceConfig;
import com.github.stimur1709.cloudops.resource.config.ResourceConfigMapper;
import com.github.stimur1709.cloudops.resource.persistence.ResourceEntity;

public record ResourceResponse(
        Long id,
        String name,
        ResourceType type,
        ResourceStatus status,
        Long organizationId,
        ResourceConfig config,
        Instant createdAt,
        Instant updatedAt
) {

    static ResourceResponse from(ResourceEntity resource, ResourceConfigMapper configMapper) {
        return new ResourceResponse(
                resource.id(),
                resource.name(),
                resource.type(),
                resource.status(),
                resource.organizationId(),
                configMapper.fromJson(resource.type(), resource.config()),
                resource.createdAt(),
                resource.updatedAt()
        );
    }
}
