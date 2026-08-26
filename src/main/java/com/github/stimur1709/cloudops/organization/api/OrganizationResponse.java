package com.github.stimur1709.cloudops.organization.api;

import java.time.Instant;

import com.github.stimur1709.cloudops.organization.persistence.OrganizationEntity;

public record OrganizationResponse(Long id, String name, Instant createdAt, Instant updatedAt) {

    static OrganizationResponse from(OrganizationEntity organization) {
        return new OrganizationResponse(
                organization.id(), organization.name(), organization.createdAt(), organization.updatedAt()
        );
    }
}
