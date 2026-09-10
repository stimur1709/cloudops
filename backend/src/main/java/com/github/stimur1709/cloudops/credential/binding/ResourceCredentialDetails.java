package com.github.stimur1709.cloudops.credential.binding;

import com.github.stimur1709.cloudops.credential.CredentialPurpose;
import com.github.stimur1709.cloudops.credential.CredentialType;
import java.time.Instant;

public record ResourceCredentialDetails(
        CredentialPurpose purpose,
        long credentialId,
        long organizationId,
        String name,
        CredentialType type,
        String username,
        Instant createdAt,
        Instant updatedAt) {}
