package com.github.stimur1709.cloudops.auth.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "Email is required")
        @Email(message = "Email must be valid")
        @Size(max = 254, message = "Email must not be longer than 254 characters")
        String email,

        @NotBlank(message = "Display name is required")
        @Size(max = 100, message = "Display name must not be longer than 100 characters")
        String displayName,

        @NotNull(message = "Password is required")
        @Size(min = 12, max = 72, message = "Password must contain from 12 to 72 characters")
        String password
) {
    public RegisterRequest {
        email = email == null ? null : email.strip();
    }
}
