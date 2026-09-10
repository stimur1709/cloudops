package com.github.stimur1709.cloudops.common.application;

public final class ForbiddenException extends RuntimeException {

    public ForbiddenException() {
        super("You do not have permission to perform this operation");
    }
}
