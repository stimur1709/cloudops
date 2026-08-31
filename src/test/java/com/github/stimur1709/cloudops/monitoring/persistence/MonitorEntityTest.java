package com.github.stimur1709.cloudops.monitoring.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import com.github.stimur1709.cloudops.monitoring.HealthStatus;
import com.github.stimur1709.cloudops.probe.ProbeType;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class MonitorEntityTest {
    private static final Instant NOW = Instant.parse("2026-08-28T12:00:00Z");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void appliesThresholdsProvidedForEachResult() {
        MonitorEntity monitor = MonitorEntity.create(1, ProbeType.HTTP_CHECK, NOW);
        record(monitor, true, 2, 2);
        record(monitor, false, 2, 2);
        assertThat(monitor.healthStatus()).isEqualTo(HealthStatus.UP);
        record(monitor, false, 2, 2);
        assertThat(monitor.healthStatus()).isEqualTo(HealthStatus.DOWN);
        record(monitor, true, 2, 2);
        record(monitor, true, 2, 2);
        assertThat(monitor.healthStatus()).isEqualTo(HealthStatus.UP);
    }

    @Test
    void changedThresholdAffectsExistingMonitorWithoutRecreation() {
        MonitorEntity monitor = MonitorEntity.create(1, ProbeType.HTTP_CHECK, NOW);
        record(monitor, true, 3, 2);
        record(monitor, false, 3, 2);
        record(monitor, false, 1, 2);
        assertThat(monitor.healthStatus()).isEqualTo(HealthStatus.DOWN);
    }

    private void record(MonitorEntity monitor, boolean success, int failure, int recovery) {
        monitor.record(NOW, MAPPER.createObjectNode().put("success", success), success, failure, recovery);
    }
}
