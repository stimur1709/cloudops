package com.github.stimur1709.cloudops.resource.application;

public final class ResourceNameConflictException extends RuntimeException {

    public ResourceNameConflictException() {
        super("Resource name is already used in this organization");
    }
}
