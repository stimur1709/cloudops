package com.github.stimur1709.cloudops.task.api;

import com.github.stimur1709.cloudops.task.TaskErrorCode;
import com.github.stimur1709.cloudops.task.TaskStatus;
import com.github.stimur1709.cloudops.task.TaskType;
import com.github.stimur1709.cloudops.task.persistence.TaskEntity;
import com.github.stimur1709.cloudops.task.runcommand.RunCommandParameters;
import com.github.stimur1709.cloudops.task.runcommand.RunCommandResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import tools.jackson.databind.JsonNode;

public record TaskResponse(
        long id,
        long organizationId,
        long resourceId,
        TaskType type,

        @Schema(
                implementation = RunCommandParameters.class,
                requiredMode = Schema.RequiredMode.REQUIRED,
                description =
                        "Parameters for RUN_COMMAND; selected by the sibling type field. No nested type discriminator.")
        JsonNode parameters,

        TaskStatus status,
        long createdBy,
        Instant createdAt,
        @Schema(nullable = true) Instant startedAt,
        @Schema(nullable = true) Instant completedAt,

        @Schema(
                implementation = RunCommandResult.class,
                nullable = true,
                description =
                        "RUN_COMMAND output when execution produced a result; null before execution or on failures without output.")
        JsonNode result,

        @Schema(nullable = true) TaskErrorCode errorCode,
        @Schema(nullable = true) String errorMessage,
        int attemptCount,
        @Schema(nullable = true) Instant lastAttemptAt,
        int recoveryCount) {
    public static TaskResponse from(TaskEntity task) {
        return new TaskResponse(
                task.id(),
                task.organizationId(),
                task.resourceId(),
                task.type(),
                task.parameters(),
                task.status(),
                task.createdBy(),
                task.createdAt(),
                task.startedAt(),
                task.completedAt(),
                task.result(),
                task.errorCode(),
                task.errorMessage(),
                task.attemptCount(),
                task.lastAttemptAt(),
                task.recoveryCount());
    }
}
