package com.github.stimur1709.cloudops.resource.application;

public final class InvalidResourceSearchException extends RuntimeException {

    private final String field;

    public InvalidResourceSearchException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String field() {
        return field;
    }
}
