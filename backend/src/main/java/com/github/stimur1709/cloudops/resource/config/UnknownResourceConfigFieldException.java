package com.github.stimur1709.cloudops.resource.config;

public class UnknownResourceConfigFieldException extends RuntimeException {

    private final String field;

    public UnknownResourceConfigFieldException(String field) {
        super("Unknown resource config field: " + field);
        this.field = field;
    }

    public String field() {
        return field;
    }
}
