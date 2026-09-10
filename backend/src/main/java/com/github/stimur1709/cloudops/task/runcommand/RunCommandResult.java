package com.github.stimur1709.cloudops.task.runcommand;

import io.swagger.v3.oas.annotations.media.Schema;

public record RunCommandResult(
        @Schema(nullable = true, description = "Null when the remote process did not provide an exit status")
        Integer exitCode,

        String stdout,
        String stderr,
        long durationMs,
        boolean outputTruncated) {}
