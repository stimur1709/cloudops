package com.github.stimur1709.cloudops.organization.application;

public final class OrganizationNotFoundException extends RuntimeException {

    public OrganizationNotFoundException(long id) {
        super("Organization with id %d was not found".formatted(id));
    }
}
