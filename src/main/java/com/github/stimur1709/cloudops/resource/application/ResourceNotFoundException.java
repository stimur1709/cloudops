package com.github.stimur1709.cloudops.resource.application;

public final class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(long id) {
        super("Resource with id %d was not found".formatted(id));
    }
}

