package com.github.stimur1709.cloudops.monitoring.settings.api.validation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ProbeSettingsRequestValidator.class)
public @interface ValidProbeSettings {
    String message() default "Invalid probe settings";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
