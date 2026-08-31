package com.github.stimur1709.cloudops.credential.binding;

import com.github.stimur1709.cloudops.credential.CredentialPurpose;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

@Entity
@Table(name = "resource_credentials")
@IdClass(ResourceCredentialId.class)
public class ResourceCredentialEntity {
    @Id
    @Column(name = "resource_id", nullable = false)
    private Long resourceId;

    @Id
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CredentialPurpose purpose;

    @Column(name = "credential_id", nullable = false)
    private Long credentialId;

    protected ResourceCredentialEntity() {}

    public ResourceCredentialEntity(long resourceId, CredentialPurpose purpose, long credentialId) {
        this.resourceId = resourceId;
        this.purpose = purpose;
        this.credentialId = credentialId;
    }

    public void replace(long credentialId) {
        this.credentialId = credentialId;
    }

    public Long resourceId() {
        return resourceId;
    }

    public CredentialPurpose purpose() {
        return purpose;
    }

    public Long credentialId() {
        return credentialId;
    }
}
