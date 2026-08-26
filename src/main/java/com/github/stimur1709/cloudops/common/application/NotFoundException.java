package com.github.stimur1709.cloudops.common.application;

import java.util.Locale;
import java.util.Objects;

public final class NotFoundException extends RuntimeException {

    private final String code;

    public NotFoundException(String entityName) {
        super(Objects.requireNonNull(entityName) + " not found");
        this.code = entityName.toUpperCase(Locale.ROOT) + "_NOT_FOUND";
    }

    public String code() {
        return code;
    }
}
