package com.github.stimur1709.cloudops.task.api.validation;

import com.github.stimur1709.cloudops.task.api.CreateTaskRequest;
import com.github.stimur1709.cloudops.task.parameters.TaskParameterCodecRegistry;
import com.github.stimur1709.cloudops.task.parameters.TaskParameterValidationException;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class ValidTaskParametersValidator implements ConstraintValidator<ValidTaskParameters, CreateTaskRequest> {

    private final TaskParameterCodecRegistry registry;

    public ValidTaskParametersValidator(TaskParameterCodecRegistry registry) {
        this.registry = registry;
    }

    @Override
    public boolean isValid(CreateTaskRequest request, ConstraintValidatorContext context) {
        if (request == null || request.type() == null) {
            return true;
        }
        try {
            registry.decode(request.type(), request.parameters());
            return true;
        } catch (TaskParameterValidationException exception) {
            context.disableDefaultConstraintViolation();
            for (TaskParameterValidationException.ParameterError error : exception.errors()) {
                String[] path = error.field().split("\\.");
                var node = context.buildConstraintViolationWithTemplate(error.message())
                        .addPropertyNode(path[0]);
                for (int index = 1; index < path.length; index++) {
                    node = node.addPropertyNode(path[index]);
                }
                node.addConstraintViolation();
            }
            return false;
        }
    }
}
