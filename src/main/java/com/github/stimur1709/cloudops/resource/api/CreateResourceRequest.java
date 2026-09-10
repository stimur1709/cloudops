package com.github.stimur1709.cloudops.resource.api;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.github.stimur1709.cloudops.resource.ResourceStatus;
import com.github.stimur1709.cloudops.resource.ResourceType;
import com.github.stimur1709.cloudops.resource.config.DatabaseResourceConfig;
import com.github.stimur1709.cloudops.resource.config.NetworkDeviceResourceConfig;
import com.github.stimur1709.cloudops.resource.config.OtherResourceConfig;
import com.github.stimur1709.cloudops.resource.config.ResourceConfig;
import com.github.stimur1709.cloudops.resource.config.ServerResourceConfig;
import com.github.stimur1709.cloudops.resource.config.ServiceResourceConfig;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateResourceRequest(
        @NotBlank(message = "Name must not be blank") @Size(max = 100, message = "Name must be at most 100 characters") String name,

        @NotNull(message = "Type is required") ResourceType type,
        @NotNull(message = "Status is required") ResourceStatus status,

        @NotNull(message = "Organization id is required") @Positive(message = "Organization id must be positive") Long organizationId,

        @NotNull(message = "Config is required") @Valid @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXTERNAL_PROPERTY, property = "type")
        @Schema(
                oneOf = {
                    ServerResourceConfig.class,
                    NetworkDeviceResourceConfig.class,
                    DatabaseResourceConfig.class,
                    ServiceResourceConfig.class,
                    OtherResourceConfig.class
                },
                description = "Selected by the sibling type field; config has no nested discriminator")
        ResourceConfig config) {}
