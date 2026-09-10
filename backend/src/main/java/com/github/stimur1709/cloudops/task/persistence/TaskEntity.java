package com.github.stimur1709.cloudops.task.persistence;

import com.github.stimur1709.cloudops.task.TaskErrorCode;
import com.github.stimur1709.cloudops.task.TaskStatus;
import com.github.stimur1709.cloudops.task.TaskType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import tools.jackson.databind.JsonNode;

@Entity
@Table(name = "tasks")
public class TaskEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    @Column(name = "resource_id", nullable = false)
    private Long resourceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TaskType type;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private JsonNode parameters;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TaskStatus status;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private JsonNode result;

    @Enumerated(EnumType.STRING)
    @Column(name = "error_code", length = 30)
    private TaskErrorCode errorCode;

    @Column(name = "error_message", length = 255)
    private String errorMessage;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "last_attempt_at")
    private Instant lastAttemptAt;

    @Column(name = "execution_id")
    private UUID executionId;

    @Column(name = "lease_expires_at")
    private Instant leaseExpiresAt;

    @Column(name = "recovery_count", nullable = false)
    private int recoveryCount;

    protected TaskEntity() {}

    private TaskEntity(
            long organizationId,
            long resourceId,
            TaskType type,
            JsonNode parameters,
            long createdBy,
            Instant createdAt) {
        this.organizationId = organizationId;
        this.resourceId = resourceId;
        this.type = type;
        this.parameters = parameters;
        this.status = TaskStatus.PENDING;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
    }

    public static TaskEntity create(
            long organizationId, long resourceId, TaskType type, JsonNode parameters, long createdBy, Instant now) {
        return new TaskEntity(organizationId, resourceId, type, parameters, createdBy, now);
    }

    public void start(Instant now) {
        requireStatus(TaskStatus.PENDING);
        status = TaskStatus.RUNNING;
        startedAt = now;
    }

    public void complete(JsonNode result, Instant now) {
        requireStatus(TaskStatus.RUNNING);
        this.status = TaskStatus.COMPLETED;
        this.result = result;
        this.completedAt = now;
        this.executionId = null;
        this.leaseExpiresAt = null;
    }

    public void fail(TaskErrorCode errorCode, String errorMessage, Instant now) {
        requireStatus(TaskStatus.RUNNING);
        this.status = TaskStatus.FAILED;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.completedAt = now;
        this.executionId = null;
        this.leaseExpiresAt = null;
    }

    public void recover() {
        requireStatus(TaskStatus.RUNNING);
        status = TaskStatus.PENDING;
        startedAt = null;
        executionId = null;
        leaseExpiresAt = null;
        recoveryCount++;
    }

    public void failRecovery(String message, Instant now) {
        requireStatus(TaskStatus.RUNNING);
        status = TaskStatus.FAILED;
        errorCode = TaskErrorCode.RECOVERY_EXHAUSTED;
        errorMessage = message;
        completedAt = now;
        executionId = null;
        leaseExpiresAt = null;
    }

    private void requireStatus(TaskStatus expected) {
        if (status != expected) {
            throw new IllegalStateException("Task must be in %s status, but was %s".formatted(expected, status));
        }
    }

    public Long id() {
        return id;
    }

    public Long organizationId() {
        return organizationId;
    }

    public Long resourceId() {
        return resourceId;
    }

    public TaskType type() {
        return type;
    }

    public JsonNode parameters() {
        return parameters;
    }

    public TaskStatus status() {
        return status;
    }

    public Long createdBy() {
        return createdBy;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant startedAt() {
        return startedAt;
    }

    public Instant completedAt() {
        return completedAt;
    }

    public JsonNode result() {
        return result;
    }

    public TaskErrorCode errorCode() {
        return errorCode;
    }

    public String errorMessage() {
        return errorMessage;
    }

    public int attemptCount() {
        return attemptCount;
    }

    public Instant lastAttemptAt() {
        return lastAttemptAt;
    }

    public UUID executionId() {
        return executionId;
    }

    public Instant leaseExpiresAt() {
        return leaseExpiresAt;
    }

    public int recoveryCount() {
        return recoveryCount;
    }
}
