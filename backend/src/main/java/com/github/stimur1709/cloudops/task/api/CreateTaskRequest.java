package com.github.stimur1709.cloudops.task.api;

import com.github.stimur1709.cloudops.task.TaskType;
import com.github.stimur1709.cloudops.task.api.validation.SupportedTaskType;
import com.github.stimur1709.cloudops.task.api.validation.ValidTaskParameters;
import com.github.stimur1709.cloudops.task.runcommand.RunCommandParameters;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import tools.jackson.databind.JsonNode;

@ValidTaskParameters
public record CreateTaskRequest(
        @NotNull(message = "Type is required") @SupportedTaskType
        TaskType type,

        @Schema(
                implementation = RunCommandParameters.class,
                requiredMode = Schema.RequiredMode.REQUIRED,
                description =
                        "Parameters for RUN_COMMAND; selected by the sibling type field. No nested type discriminator.")
        JsonNode parameters) {}
