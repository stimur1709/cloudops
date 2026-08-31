package com.github.stimur1709.cloudops.credential.api;

import java.time.Instant;

import com.github.stimur1709.cloudops.credential.CredentialType;
import com.github.stimur1709.cloudops.credential.persistence.CredentialEntity;

public record CredentialResponse(
        long id,
        long organizationId,
        String name,
        CredentialType type,
        String username,
        Instant createdAt,
        Instant updatedAt
) {
    public static CredentialResponse from(CredentialEntity entity) {
        return new CredentialResponse(entity.id(), entity.organizationId(), entity.name(), entity.type(),
                entity.username(), entity.createdAt(), entity.updatedAt());
    }
}
