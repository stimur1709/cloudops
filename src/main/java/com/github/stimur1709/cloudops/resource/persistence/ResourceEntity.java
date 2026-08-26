package com.github.stimur1709.cloudops.resource.persistence;

import java.time.Instant;

import com.github.stimur1709.cloudops.resource.ResourceStatus;
import com.github.stimur1709.cloudops.resource.ResourceType;
import com.github.stimur1709.cloudops.organization.persistence.OrganizationEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "resources")
public class ResourceEntity {

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

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private OrganizationEntity organization;

    @Column(name = "organization_id", nullable = false, insertable = false, updatable = false)
    private Long organizationId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ResourceEntity() {
    }

    private ResourceEntity(
            String name,
            ResourceType type,
            ResourceStatus status,
            OrganizationEntity organization,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.name = name;
        this.type = type;
        this.status = status;
        this.organization = organization;
        this.organizationId = organization.id();
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static ResourceEntity create(
            String name,
            ResourceType type,
            ResourceStatus status,
            OrganizationEntity organization,
            Instant createdAt
    ) {
        return new ResourceEntity(name, type, status, organization, createdAt, createdAt);
    }

    public void update(
            String name,
            ResourceType type,
            ResourceStatus status,
            OrganizationEntity organization,
            Instant updatedAt
    ) {
        this.name = name;
        this.type = type;
        this.status = status;
        this.organization = organization;
        this.organizationId = organization.id();
        this.updatedAt = updatedAt;
    }

    public Long id() {
        return id;
    }

    public String name() {
        return name;
    }

    public ResourceType type() {
        return type;
    }

    public ResourceStatus status() {
        return status;
    }

    public Long organizationId() {
        return organizationId;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
