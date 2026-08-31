package com.github.stimur1709.cloudops.organization.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "organizations")
public class OrganizationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected OrganizationEntity() {}

    private OrganizationEntity(String name, Instant createdAt) {
        this.name = name;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    public static OrganizationEntity create(String name, Instant createdAt) {
        return new OrganizationEntity(name, createdAt);
    }

    public void update(String name, Instant updatedAt) {
        this.name = name;
        this.updatedAt = updatedAt;
    }

    public Long id() {
        return id;
    }

    public String name() {
        return name;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
