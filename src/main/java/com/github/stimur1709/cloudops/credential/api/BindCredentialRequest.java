package com.github.stimur1709.cloudops.credential.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record BindCredentialRequest(
        @NotNull(message = "Credential id is required") @Positive(message = "Credential id must be positive")
        Long credentialId
) {
}
