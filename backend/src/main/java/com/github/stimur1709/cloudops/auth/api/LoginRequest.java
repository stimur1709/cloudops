package com.github.stimur1709.cloudops.auth.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank(message = "Email is required") @Email(message = "Email must be valid") @Size(max = 254, message = "Email must not be longer than 254 characters") String email,

        @NotNull(message = "Password is required") String password) {
    public LoginRequest {
        email = email == null ? null : email.strip();
    }
}
