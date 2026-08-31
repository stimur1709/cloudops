package com.github.stimur1709.cloudops.monitoring.persistence;

import com.github.stimur1709.cloudops.monitoring.ResourceHealthStatus;
import com.github.stimur1709.cloudops.resource.persistence.ResourceEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.Table;

@Entity
@Table(name = "resource_health")
public class ResourceHealthEntity {

    @Id
    @Column(name = "resource_id")
    private Long resourceId;

    @OneToOne(optional = false, fetch = FetchType.LAZY)
    @MapsId
    @PrimaryKeyJoinColumn(name = "resource_id")
    private ResourceEntity resource;

    @Enumerated(EnumType.STRING)
    @Column(name = "health_status", nullable = false, length = 10)
    private ResourceHealthStatus healthStatus;

    protected ResourceHealthEntity() {}

    private ResourceHealthEntity(ResourceEntity resource) {
        this.resource = resource;
        this.healthStatus = ResourceHealthStatus.UNKNOWN;
    }

    public static ResourceHealthEntity create(ResourceEntity resource) {
        return new ResourceHealthEntity(resource);
    }

    public void update(ResourceHealthStatus healthStatus) {
        this.healthStatus = healthStatus;
    }

    public Long resourceId() {
        return resourceId;
    }

    public ResourceEntity resource() {
        return resource;
    }

    public ResourceHealthStatus healthStatus() {
        return healthStatus;
    }
}
