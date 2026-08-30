package com.github.stimur1709.cloudops.task.api;

import com.github.stimur1709.cloudops.task.api.validation.SupportedTaskType;
import jakarta.validation.constraints.NotNull;

public record CreateTaskRequest(
        @NotNull(message = "Type is required")
        @SupportedTaskType
        String type
) {
}
