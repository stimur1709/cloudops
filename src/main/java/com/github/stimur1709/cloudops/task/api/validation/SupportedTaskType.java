package com.github.stimur1709.cloudops.task.api.validation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = SupportedTaskTypeValidator.class)
public @interface SupportedTaskType {
    String message() default "Task type is not available";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
