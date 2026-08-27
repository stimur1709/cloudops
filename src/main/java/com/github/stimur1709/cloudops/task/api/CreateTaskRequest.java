package com.github.stimur1709.cloudops.task.api;

import com.github.stimur1709.cloudops.task.TaskType;
import jakarta.validation.constraints.NotNull;

public record CreateTaskRequest(
        @NotNull(message = "Type is required")
        TaskType type
) {
}
