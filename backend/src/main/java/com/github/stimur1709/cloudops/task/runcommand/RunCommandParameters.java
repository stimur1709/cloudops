package com.github.stimur1709.cloudops.task.runcommand;

import com.github.stimur1709.cloudops.task.parameters.TaskParameters;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RunCommandParameters(
        @NotBlank(message = "Command is required") @Size(max = 4096, message = "Command must not exceed 4096 characters") String command)
        implements TaskParameters {}
