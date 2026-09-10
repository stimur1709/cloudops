package com.github.stimur1709.cloudops.credential.persistence;

import com.github.stimur1709.cloudops.credential.CredentialType;
import com.github.stimur1709.cloudops.organization.persistence.OrganizationEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "credentials")
public class CredentialEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private OrganizationEntity organization;

    @Column(name = "organization_id", nullable = false, insertable = false, updatable = false)
    private Long organizationId;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private CredentialType type;

    @Column(length = 255)
    private String username;

    @Column(name = "secret_encrypted", nullable = false, columnDefinition = "text")
    private String secretEncrypted;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CredentialEntity() {}

    private CredentialEntity(
            OrganizationEntity organization,
            String name,
            CredentialType type,
            String username,
            String secretEncrypted,
            Instant now) {
        this.organization = organization;
        this.organizationId = organization.id();
        this.name = name;
        this.type = type;
        this.username = username;
        this.secretEncrypted = secretEncrypted;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static CredentialEntity create(
            OrganizationEntity organization,
            String name,
            CredentialType type,
            String username,
            String secretEncrypted,
            Instant now) {
        return new CredentialEntity(organization, name, type, username, secretEncrypted, now);
    }

    public void update(String name, CredentialType type, String username, String secretEncrypted, Instant now) {
        this.name = name;
        this.type = type;
        this.username = username;
        this.secretEncrypted = secretEncrypted;
        this.updatedAt = now;
    }

    public Long id() {
        return id;
    }

    public Long organizationId() {
        return organizationId;
    }

    public String name() {
        return name;
    }

    public CredentialType type() {
        return type;
    }

    public String username() {
        return username;
    }

    public String secretEncrypted() {
        return secretEncrypted;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
