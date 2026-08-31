package com.github.stimur1709.cloudops.organization.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateOrganizationRequest(
        @NotBlank(message = "Name must not be blank") @Size(max = 100, message = "Name must be at most 100 characters") String name) {}
