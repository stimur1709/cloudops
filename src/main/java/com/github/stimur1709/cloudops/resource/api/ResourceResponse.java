package com.github.stimur1709.cloudops.resource.api;

import java.time.Instant;

import com.github.stimur1709.cloudops.resource.ResourceStatus;
import com.github.stimur1709.cloudops.resource.ResourceType;
import com.github.stimur1709.cloudops.resource.persistence.ResourceEntity;

public record ResourceResponse(
        Long id,
        String name,
        ResourceType type,
        ResourceStatus status,
        Instant createdAt,
        Instant updatedAt
) {

    static ResourceResponse from(ResourceEntity resource) {
        return new ResourceResponse(
                resource.id(),
                resource.name(),
                resource.type(),
                resource.status(),
                resource.createdAt(),
                resource.updatedAt()
        );
    }
}
