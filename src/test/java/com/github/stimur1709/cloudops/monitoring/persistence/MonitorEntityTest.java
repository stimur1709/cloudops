package com.github.stimur1709.cloudops.monitoring.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import com.github.stimur1709.cloudops.monitoring.HealthStatus;
import com.github.stimur1709.cloudops.monitoring.StorageMode;
import com.github.stimur1709.cloudops.probe.ProbeType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import tools.jackson.databind.ObjectMapper;

class MonitorEntityTest {

    private static final Instant NOW = Instant.parse("2026-08-28T12:00:00Z");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @ParameterizedTest
    @EnumSource(StorageMode.class)
    void appliesFailureAndRecoveryThresholdsForEveryStorageMode(StorageMode storageMode) {
        MonitorEntity monitor = monitor(storageMode, 2, 2);

        record(monitor, true);
        assertState(monitor, HealthStatus.UP, 0, 0);

        record(monitor, false);
        assertState(monitor, HealthStatus.UP, 1, 0);
        record(monitor, true);
        assertState(monitor, HealthStatus.UP, 0, 0);
        record(monitor, false);
        record(monitor, false);
        assertState(monitor, HealthStatus.DOWN, 0, 0);

        record(monitor, true);
        assertState(monitor, HealthStatus.DOWN, 0, 1);
        record(monitor, false);
        assertState(monitor, HealthStatus.DOWN, 0, 0);
        record(monitor, true);
        record(monitor, true);
        assertState(monitor, HealthStatus.UP, 0, 0);
    }

    @Test
    void firstResultDeterminesHealthWithoutThresholdDelay() {
        MonitorEntity successful = monitor(StorageMode.LATEST_ONLY, 10, 10);
        MonitorEntity failed = monitor(StorageMode.HISTORY, 10, 10);

        record(successful, true);
        record(failed, false);

        assertThat(successful.healthStatus()).isEqualTo(HealthStatus.UP);
        assertThat(failed.healthStatus()).isEqualTo(HealthStatus.DOWN);
    }

    @Test
    void thresholdOfOneSwitchesKnownHealthImmediately() {
        MonitorEntity monitor = monitor(StorageMode.LATEST_ONLY, 1, 1);

        record(monitor, true);
        record(monitor, false);
        assertThat(monitor.healthStatus()).isEqualTo(HealthStatus.DOWN);

        record(monitor, true);
        assertThat(monitor.healthStatus()).isEqualTo(HealthStatus.UP);
    }

    @Test
    void changingThresholdsPreservesHealthAndResetsCounters() {
        MonitorEntity monitor = monitor(StorageMode.LATEST_ONLY, 3, 2);
        record(monitor, true);
        record(monitor, false);
        assertState(monitor, HealthStatus.UP, 1, 0);

        monitor.update(true, 30, StorageMode.LATEST_ONLY, null, 4, 3, NOW);

        assertState(monitor, HealthStatus.UP, 0, 0);
        assertThat(monitor.failureThreshold()).isEqualTo(4);
        assertThat(monitor.recoveryThreshold()).isEqualTo(3);
    }

    private MonitorEntity monitor(StorageMode storageMode, int failureThreshold, int recoveryThreshold) {
        return MonitorEntity.create(
                1, ProbeType.HTTP_CHECK, true, 30, NOW, storageMode,
                storageMode == StorageMode.HISTORY ? 30 : null, failureThreshold, recoveryThreshold
        );
    }

    private void record(MonitorEntity monitor, boolean success) {
        monitor.record(NOW, OBJECT_MAPPER.createObjectNode().put("success", success), success);
    }

    private void assertState(
            MonitorEntity monitor,
            HealthStatus healthStatus,
            int consecutiveFailures,
            int consecutiveSuccesses
    ) {
        assertThat(monitor.healthStatus()).isEqualTo(healthStatus);
        assertThat(monitor.consecutiveFailures()).isEqualTo(consecutiveFailures);
        assertThat(monitor.consecutiveSuccesses()).isEqualTo(consecutiveSuccesses);
    }
}
