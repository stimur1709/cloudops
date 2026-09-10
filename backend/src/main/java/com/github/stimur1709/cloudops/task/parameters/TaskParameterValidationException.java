package com.github.stimur1709.cloudops.task.parameters;

import java.util.List;

public final class TaskParameterValidationException extends RuntimeException {

    private final List<ParameterError> errors;

    public TaskParameterValidationException(List<ParameterError> errors) {
        super("Task parameters are invalid");
        this.errors = List.copyOf(errors);
    }

    public List<ParameterError> errors() {
        return errors;
    }

    public record ParameterError(String field, String message) {}
}
