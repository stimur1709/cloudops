package com.github.stimur1709.cloudops.resource.api;

import com.github.stimur1709.cloudops.monitoring.ResourceHealthStatus;
import com.github.stimur1709.cloudops.resource.ResourceStatus;
import com.github.stimur1709.cloudops.resource.ResourceType;
import com.github.stimur1709.cloudops.resource.application.ResourceDetails;
import com.github.stimur1709.cloudops.resource.config.DatabaseResourceConfig;
import com.github.stimur1709.cloudops.resource.config.NetworkDeviceResourceConfig;
import com.github.stimur1709.cloudops.resource.config.OtherResourceConfig;
import com.github.stimur1709.cloudops.resource.config.ResourceConfig;
import com.github.stimur1709.cloudops.resource.config.ResourceConfigMapper;
import com.github.stimur1709.cloudops.resource.config.ServerResourceConfig;
import com.github.stimur1709.cloudops.resource.config.ServiceResourceConfig;
import com.github.stimur1709.cloudops.resource.persistence.ResourceEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

public record ResourceResponse(
        Long id,
        String name,
        ResourceType type,
        ResourceStatus status,
        ResourceHealthStatus healthStatus,
        Long organizationId,

        @Schema(
                oneOf = {
                    ServerResourceConfig.class,
                    NetworkDeviceResourceConfig.class,
                    DatabaseResourceConfig.class,
                    ServiceResourceConfig.class,
                    OtherResourceConfig.class
                },
                description = "Selected by the sibling type field; config has no nested discriminator")
        ResourceConfig config,

        Instant createdAt,
        Instant updatedAt) {

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
                resource.updatedAt());
    }
}
