package com.github.stimur1709.cloudops.monitoring.persistence;

import com.github.stimur1709.cloudops.monitoring.ResourceHealthStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "resource_health_events")
public class ResourceHealthEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "resource_id", nullable = false)
    private Long resourceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", nullable = false, length = 10)
    private ResourceHealthStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 10)
    private ResourceHealthStatus toStatus;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    protected ResourceHealthEventEntity() {}

    private ResourceHealthEventEntity(
            long resourceId, ResourceHealthStatus fromStatus, ResourceHealthStatus toStatus, Instant changedAt) {
        this.resourceId = resourceId;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.changedAt = changedAt;
    }

    public static ResourceHealthEventEntity create(
            long resourceId, ResourceHealthStatus fromStatus, ResourceHealthStatus toStatus, Instant changedAt) {
        return new ResourceHealthEventEntity(resourceId, fromStatus, toStatus, changedAt);
    }

    public Long id() {
        return id;
    }

    public Long resourceId() {
        return resourceId;
    }

    public ResourceHealthStatus fromStatus() {
        return fromStatus;
    }

    public ResourceHealthStatus toStatus() {
        return toStatus;
    }

    public Instant changedAt() {
        return changedAt;
    }
}
