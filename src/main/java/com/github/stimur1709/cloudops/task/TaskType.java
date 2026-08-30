package com.github.stimur1709.cloudops.task;

import java.util.Objects;

public record TaskType(String value) {

    public TaskType {
        Objects.requireNonNull(value);
        if (!value.matches("[A-Z][A-Z0-9_]{0,29}")) {
            throw new IllegalArgumentException("Task type has an invalid format");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
