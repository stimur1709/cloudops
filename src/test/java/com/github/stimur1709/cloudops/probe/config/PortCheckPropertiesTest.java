package com.github.stimur1709.cloudops.probe.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

class PortCheckPropertiesTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void acceptsPositiveTimeoutAndRejectsMissingOrNonPositiveValues() {
        assertThat(validator.validate(new PortCheckProperties(Duration.ofSeconds(3)))).isEmpty();
        assertThat(validator.validate(new PortCheckProperties(null)))
                .extracting(violation -> violation.getPropertyPath().toString())
                .containsExactly("timeout");
        assertThat(validator.validate(new PortCheckProperties(Duration.ZERO)))
                .extracting(violation -> violation.getPropertyPath().toString())
                .containsExactly("timeout");
    }
}
