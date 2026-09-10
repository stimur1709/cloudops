package com.github.stimur1709.cloudops.common.application;

import java.util.Objects;

public final class ConflictException extends RuntimeException {

    private final String code;

    public ConflictException(String code, String message) {
        super(Objects.requireNonNull(message));
        this.code = Objects.requireNonNull(code);
    }

    public String code() {
        return code;
    }
}
