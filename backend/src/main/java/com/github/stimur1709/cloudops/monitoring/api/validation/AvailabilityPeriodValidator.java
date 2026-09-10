package com.github.stimur1709.cloudops.monitoring.api.validation;

import com.github.stimur1709.cloudops.monitoring.api.ResourceAvailabilityRequest;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public final class AvailabilityPeriodValidator
        implements ConstraintValidator<ValidAvailabilityPeriod, ResourceAvailabilityRequest> {

    @Override
    public boolean isValid(ResourceAvailabilityRequest request, ConstraintValidatorContext context) {
        if (request == null || request.from() == null || request.to() == null) {
            return true;
        }
        if (request.from().isBefore(request.to())) {
            return true;
        }

        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate("To must be after from")
                .addPropertyNode("to")
                .addConstraintViolation();
        return false;
    }
}
