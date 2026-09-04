package com.github.stimur1709.cloudops.task.api;

import com.github.stimur1709.cloudops.task.TaskType;
import com.github.stimur1709.cloudops.task.api.validation.SupportedTaskType;
import com.github.stimur1709.cloudops.task.api.validation.ValidTaskParameters;
import jakarta.validation.constraints.NotNull;
import tools.jackson.databind.JsonNode;

@ValidTaskParameters
public record CreateTaskRequest(
        @NotNull(message = "Type is required") @SupportedTaskType
        TaskType type,

        JsonNode parameters) {}
