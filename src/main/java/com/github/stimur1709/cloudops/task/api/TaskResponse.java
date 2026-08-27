package com.github.stimur1709.cloudops.task.api;

import java.time.Instant;

import com.github.stimur1709.cloudops.task.TaskErrorCode;
import com.github.stimur1709.cloudops.task.TaskStatus;
import com.github.stimur1709.cloudops.task.TaskType;
import com.github.stimur1709.cloudops.task.persistence.TaskEntity;
import tools.jackson.databind.JsonNode;

public record TaskResponse(
        long id,
        long organizationId,
        long resourceId,
        TaskType type,
        TaskStatus status,
        long createdBy,
        Instant createdAt,
        Instant startedAt,
        Instant completedAt,
        JsonNode result,
        TaskErrorCode errorCode,
        String errorMessage
) {
    public static TaskResponse from(TaskEntity task) {
        return new TaskResponse(
                task.id(), task.organizationId(), task.resourceId(), task.type(), task.status(), task.createdBy(),
                task.createdAt(), task.startedAt(), task.completedAt(), task.result(), task.errorCode(),
                task.errorMessage()
        );
    }
}
