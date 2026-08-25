package com.github.stimur1709.cloudops.resource.infrastructure.persistence;

import java.time.Instant;

import com.github.stimur1709.cloudops.resource.domain.Resource;
import com.github.stimur1709.cloudops.resource.domain.ResourceStatus;
import com.github.stimur1709.cloudops.resource.domain.ResourceType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "resources")
class ResourceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ResourceType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ResourceStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ResourceEntity() {
    }

    private ResourceEntity(Resource resource) {
        this.id = resource.id();
        this.name = resource.name();
        this.type = resource.type();
        this.status = resource.status();
        this.createdAt = resource.createdAt();
        this.updatedAt = resource.updatedAt();
    }

    static ResourceEntity from(Resource resource) {
        return new ResourceEntity(resource);
    }

    Resource toDomain() {
        return new Resource(id, name, type, status, createdAt, updatedAt);
    }
}

