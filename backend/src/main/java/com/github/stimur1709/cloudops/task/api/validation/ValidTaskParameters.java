package com.github.stimur1709.cloudops.task.api.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ValidTaskParametersValidator.class)
public @interface ValidTaskParameters {
    String message() default "Task parameters are invalid";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
