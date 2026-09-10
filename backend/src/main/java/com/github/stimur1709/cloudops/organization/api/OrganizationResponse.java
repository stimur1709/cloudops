package com.github.stimur1709.cloudops.organization.api;

import com.github.stimur1709.cloudops.organization.persistence.OrganizationEntity;
import java.time.Instant;

public record OrganizationResponse(Long id, String name, Instant createdAt, Instant updatedAt) {

    static OrganizationResponse from(OrganizationEntity organization) {
        return new OrganizationResponse(
                organization.id(), organization.name(), organization.createdAt(), organization.updatedAt());
    }
}
