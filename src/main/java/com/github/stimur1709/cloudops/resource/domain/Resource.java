package com.github.stimur1709.cloudops.resource.domain;

import java.time.Instant;

public record Resource(
        Long id,
        String name,
        ResourceType type,
        ResourceStatus status,
        Instant createdAt,
        Instant updatedAt
) {
}

