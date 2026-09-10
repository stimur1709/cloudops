package com.github.stimur1709.cloudops.monitoring.api.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Constraint(validatedBy = AvailabilityPeriodValidator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidAvailabilityPeriod {

    String message() default "Availability period is invalid";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
