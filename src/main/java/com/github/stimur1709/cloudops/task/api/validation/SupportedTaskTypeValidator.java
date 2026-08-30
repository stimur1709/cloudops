package com.github.stimur1709.cloudops.task.api.validation;

import com.github.stimur1709.cloudops.task.execution.TaskHandlerRegistry;
import com.github.stimur1709.cloudops.task.TaskType;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class SupportedTaskTypeValidator implements ConstraintValidator<SupportedTaskType, TaskType> {

    private final TaskHandlerRegistry registry;

    public SupportedTaskTypeValidator(TaskHandlerRegistry registry) {
        this.registry = registry;
    }

    @Override
    public boolean isValid(TaskType value, ConstraintValidatorContext context) {
        return value == null || registry.supports(value);
    }
}
