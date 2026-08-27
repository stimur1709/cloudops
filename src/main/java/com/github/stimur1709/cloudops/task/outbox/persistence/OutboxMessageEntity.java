package com.github.stimur1709.cloudops.task.outbox.persistence;

import java.time.Instant;
import java.util.UUID;

import com.github.stimur1709.cloudops.task.outbox.OutboxAggregateType;
import com.github.stimur1709.cloudops.task.outbox.OutboxMessageType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import tools.jackson.databind.JsonNode;

@Entity
@Table(name = "outbox_messages")
public class OutboxMessageEntity {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "message_type", nullable = false, length = 50)
    private OutboxMessageType messageType;

    @Enumerated(EnumType.STRING)
    @Column(name = "aggregate_type", nullable = false, length = 30)
    private OutboxAggregateType aggregateType;

    @Column(name = "aggregate_id", nullable = false)
    private Long aggregateId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private JsonNode payload;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    protected OutboxMessageEntity() {
    }

    private OutboxMessageEntity(UUID id, long taskId, JsonNode payload, Instant createdAt) {
        this.id = id;
        this.messageType = OutboxMessageType.TASK_EXECUTION_REQUESTED;
        this.aggregateType = OutboxAggregateType.TASK;
        this.aggregateId = taskId;
        this.payload = payload;
        this.createdAt = createdAt;
    }

    public static OutboxMessageEntity taskExecutionRequested(long taskId, JsonNode payload, Instant createdAt) {
        return new OutboxMessageEntity(UUID.randomUUID(), taskId, payload, createdAt);
    }

    public void markPublished(Instant now) {
        if (publishedAt != null) {
            throw new IllegalStateException("Outbox message is already published");
        }
        publishedAt = now;
    }

    public UUID id() { return id; }
    public OutboxMessageType messageType() { return messageType; }
    public OutboxAggregateType aggregateType() { return aggregateType; }
    public Long aggregateId() { return aggregateId; }
    public JsonNode payload() { return payload; }
    public Instant createdAt() { return createdAt; }
    public Instant publishedAt() { return publishedAt; }
}
