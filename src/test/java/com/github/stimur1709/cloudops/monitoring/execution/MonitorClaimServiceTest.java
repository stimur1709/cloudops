package com.github.stimur1709.cloudops.monitoring.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.github.stimur1709.cloudops.monitoring.StorageMode;
import com.github.stimur1709.cloudops.monitoring.config.MonitoringProperties;
import com.github.stimur1709.cloudops.monitoring.settings.EffectiveProbeSettings;
import com.github.stimur1709.cloudops.monitoring.settings.MonitoringSettingsResolver;
import com.github.stimur1709.cloudops.monitoring.settings.SettingsSource;
import com.github.stimur1709.cloudops.probe.ProbeType;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class MonitorClaimServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-01T00:00:00Z");

    @Test
    void periodicClaimResolvesSettingsOnceAndReservesScheduleOnce() {
        MonitorScheduleRepository repository = mock(MonitorScheduleRepository.class);
        MonitoringSettingsResolver resolver = mock(MonitoringSettingsResolver.class);
        MonitoringProperties properties =
                new MonitoringProperties(true, Duration.ofSeconds(5), 20, 30, Duration.ofHours(1), 100, null);
        var claimed = new MonitorScheduleRepository.ClaimedMonitor(7, 8, 9, ProbeType.HTTP_CHECK);
        when(repository.claimDue(NOW, 20)).thenReturn(List.of(claimed));
        when(resolver.resolve(8, 9, ProbeType.HTTP_CHECK))
                .thenReturn(new EffectiveProbeSettings(
                        ProbeType.HTTP_CHECK,
                        true,
                        41,
                        3,
                        2,
                        StorageMode.LATEST_ONLY,
                        null,
                        500,
                        SettingsSource.ORGANIZATION));
        MonitorClaimService service =
                new MonitorClaimService(repository, resolver, properties, Clock.fixed(NOW, ZoneOffset.UTC));

        assertThat(service.claimDue()).containsExactly(7L);

        verify(resolver).resolve(8, 9, ProbeType.HTTP_CHECK);
        verify(repository).claimDue(NOW, 20);
        verify(repository).scheduleNext(7, NOW.plusSeconds(41));
        verifyNoMoreInteractions(repository, resolver);
    }
}
