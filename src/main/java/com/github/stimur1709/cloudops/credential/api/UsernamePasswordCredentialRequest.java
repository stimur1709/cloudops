package com.github.stimur1709.cloudops.credential.api;

import com.github.stimur1709.cloudops.credential.CredentialType;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UsernamePasswordCredentialRequest(
        @NotBlank(message = "Name must not be blank") @Size(max = 100) String name,

        @NotNull(message = "Type is required") CredentialType type,

        @NotBlank(message = "Username must not be blank") @Size(max = 255) String username,

        @NotBlank(message = "Password must not be blank") String password)
        implements CredentialRequest {

    @AssertTrue(message = "Type must be USERNAME_PASSWORD") public boolean isTypeValid() {
        return type == CredentialType.USERNAME_PASSWORD;
    }

    @Override
    public String secret() {
        return password;
    }
}
