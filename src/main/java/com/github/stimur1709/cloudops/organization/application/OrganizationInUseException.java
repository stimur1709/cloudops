package com.github.stimur1709.cloudops.organization.application;

public final class OrganizationInUseException extends RuntimeException {

    public OrganizationInUseException() {
        super("Organization cannot be deleted while it has resources");
    }
}
