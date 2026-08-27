package com.github.stimur1709.cloudops.monitoring.config;

import java.time.Duration;
import java.util.Set;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MonitoringPropertiesTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void acceptsPositiveDurations() {
        assertThat(validator.validate(properties(Duration.ofNanos(1), Duration.ofSeconds(1)))).isEmpty();
    }

    @Test
    void rejectsNonPositiveDurations() {
        Set<ConstraintViolation<MonitoringProperties>> violations = validator.validate(
                properties(Duration.ZERO, Duration.ofSeconds(-1))
        );

        assertThat(violations)
                .extracting(violation -> violation.getPropertyPath().toString())
                .containsExactlyInAnyOrder("pollInterval", "retentionPollInterval");
    }

    private MonitoringProperties properties(Duration pollInterval, Duration retentionPollInterval) {
        return new MonitoringProperties(true, pollInterval, 10, 30, retentionPollInterval, 10);
    }
}
