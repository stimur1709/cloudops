package com.github.stimur1709.cloudops.task.execution;

import com.github.stimur1709.cloudops.resource.ResourceStatus;
import com.github.stimur1709.cloudops.resource.config.ResourceConfig;
import com.github.stimur1709.cloudops.task.TaskType;
import tools.jackson.databind.JsonNode;

public record TaskExecutionContext(
        long resourceId,
        TaskType type,
        JsonNode parameters,
        ResourceStatus resourceStatus,
        ResourceConfig resourceConfig) {}
