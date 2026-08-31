package com.github.stimur1709.cloudops.monitoring.config;

import java.time.Duration;
import java.util.Set;
import java.util.Arrays;
import java.util.stream.Collectors;
import com.github.stimur1709.cloudops.monitoring.StorageMode;
import com.github.stimur1709.cloudops.monitoring.settings.DefaultProbeSettings;
import com.github.stimur1709.cloudops.probe.ProbeType;

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
        var defaults = Arrays.stream(ProbeType.values()).collect(Collectors.toMap(type -> type,
                type -> new DefaultProbeSettings(true, 30, 3, 2, StorageMode.LATEST_ONLY, null,
                        type == ProbeType.DNS_CHECK ? null : 500)));
        return new MonitoringProperties(true, pollInterval, 10, 30, retentionPollInterval, 10, defaults);
    }
}
