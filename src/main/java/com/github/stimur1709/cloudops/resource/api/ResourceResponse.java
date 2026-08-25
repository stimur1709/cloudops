package com.github.stimur1709.cloudops.resource.api;

import java.time.Instant;

import com.github.stimur1709.cloudops.resource.domain.Resource;
import com.github.stimur1709.cloudops.resource.domain.ResourceStatus;
import com.github.stimur1709.cloudops.resource.domain.ResourceType;

public record ResourceResponse(
        Long id,
        String name,
        ResourceType type,
        ResourceStatus status,
        Instant createdAt,
        Instant updatedAt
) {

    static ResourceResponse from(Resource resource) {
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

