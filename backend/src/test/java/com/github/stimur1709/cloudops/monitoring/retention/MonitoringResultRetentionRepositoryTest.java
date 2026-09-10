package com.github.stimur1709.cloudops.monitoring.retention;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.github.stimur1709.cloudops.monitoring.StorageMode;
import com.github.stimur1709.cloudops.monitoring.config.MonitoringProperties;
import com.github.stimur1709.cloudops.monitoring.settings.DefaultProbeSettings;
import com.github.stimur1709.cloudops.probe.ProbeType;
import java.time.Duration;
import java.util.Arrays;
import java.util.EnumMap;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class MonitoringResultRetentionRepositoryTest {

    @Test
    void derivesEveryApplicationDefaultFromProbeTypeEnum() {
        var defaults = new EnumMap<ProbeType, DefaultProbeSettings>(ProbeType.class);
        Arrays.stream(ProbeType.values())
                .forEach(type -> defaults.put(
                        type,
                        new DefaultProbeSettings(
                                true, 30, 3, 2, StorageMode.HISTORY, 7, type == ProbeType.DNS_CHECK ? null : 500)));
        var properties =
                new MonitoringProperties(false, Duration.ofSeconds(1), 10, 30, Duration.ofHours(1), 10, defaults);
        var repository = new MonitoringResultRetentionRepository(mock(JdbcTemplate.class), properties);

        var parameters = repository.applicationDefaultParameters();

        assertThat(parameters).hasSize(ProbeType.values().length * 3);
        assertThat(parameters)
                .filteredOn(String.class::isInstance)
                .containsAll(Arrays.stream(ProbeType.values()).map(Enum::name).toList());
    }
}
