package com.github.stimur1709.cloudops.monitoring.config;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNoException;

class MonitoringPropertiesTest {

    @Test
    void acceptsPositiveDurations() {
        assertThatNoException().isThrownBy(() -> properties(Duration.ofMillis(1), Duration.ofSeconds(1)));
    }

    @Test
    void rejectsNonPositiveDurations() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> properties(Duration.ZERO, Duration.ofSeconds(1)))
                .withMessage("pollInterval must be positive");

        assertThatIllegalArgumentException()
                .isThrownBy(() -> properties(Duration.ofSeconds(1), Duration.ofSeconds(-1)))
                .withMessage("retentionPollInterval must be positive");
    }

    private MonitoringProperties properties(Duration pollInterval, Duration retentionPollInterval) {
        return new MonitoringProperties(true, pollInterval, 10, 30, retentionPollInterval, 10);
    }
}
