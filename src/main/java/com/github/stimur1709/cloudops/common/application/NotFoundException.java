package com.github.stimur1709.cloudops.common.application;

public final class NotFoundException extends RuntimeException {

    private final String code;

    public NotFoundException() {
        super("Entity not found");
        this.code = "ENTITY_NOT_FOUND";
    }

    public String code() {
        return code;
    }
}
