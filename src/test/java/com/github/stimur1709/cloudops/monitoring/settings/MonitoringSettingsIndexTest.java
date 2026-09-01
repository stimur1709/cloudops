package com.github.stimur1709.cloudops.monitoring.settings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.github.stimur1709.cloudops.monitoring.StorageMode;
import com.github.stimur1709.cloudops.monitoring.config.MonitoringProperties;
import com.github.stimur1709.cloudops.monitoring.settings.persistence.OrganizationProbeSettingsEntity;
import com.github.stimur1709.cloudops.monitoring.settings.persistence.OrganizationProbeSettingsJpaRepository;
import com.github.stimur1709.cloudops.monitoring.settings.persistence.ResourceProbeSettingsEntity;
import com.github.stimur1709.cloudops.monitoring.settings.persistence.ResourceProbeSettingsJpaRepository;
import com.github.stimur1709.cloudops.probe.ProbeType;
import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import org.junit.jupiter.api.Test;

class MonitoringSettingsIndexTest {

    @Test
    void loadsOnlyExistingOverridesAndResolvesWithoutRepositoryLookups() {
        OrganizationProbeSettingsJpaRepository organizations = mock(OrganizationProbeSettingsJpaRepository.class);
        ResourceProbeSettingsJpaRepository resources = mock(ResourceProbeSettingsJpaRepository.class);
        ProbeSettingsValues organizationValues = values(false, 41);
        ProbeSettingsValues resourceValues = values(true, 52);
        when(organizations.findAll())
                .thenReturn(
                        List.of(OrganizationProbeSettingsEntity.create(10, ProbeType.HTTP_CHECK, organizationValues)));
        when(resources.findAll())
                .thenReturn(List.of(ResourceProbeSettingsEntity.create(20, ProbeType.HTTP_CHECK, resourceValues)));
        MonitoringSettingsIndex index = new MonitoringSettingsIndex(organizations, resources);
        index.reload();
        MonitoringSettingsResolver resolver = new MonitoringSettingsResolver(index, properties());

        assertThat(index.organizationOverrideCount()).isEqualTo(1);
        assertThat(index.resourceOverrideCount()).isEqualTo(1);
        assertThat(resolver.resolve(20, 10, ProbeType.HTTP_CHECK).source()).isEqualTo(SettingsSource.RESOURCE);
        assertThat(resolver.resolve(21, 10, ProbeType.HTTP_CHECK).source()).isEqualTo(SettingsSource.ORGANIZATION);
        assertThat(resolver.resolve(21, 11, ProbeType.HTTP_CHECK).source()).isEqualTo(SettingsSource.APPLICATION);

        for (long resourceId = 1000; resourceId < 2000; resourceId++) {
            resolver.resolve(resourceId, 11, ProbeType.HTTP_CHECK);
        }
        assertThat(index.resourceOverrideCount()).isEqualTo(1);
        assertThat(index.organizationOverrideCount()).isEqualTo(1);
        verify(organizations).findAll();
        verify(resources).findAll();
        verifyNoMoreInteractions(organizations, resources);
    }

    @Test
    void putAndRemoveImmediatelyRestoresFallback() {
        OrganizationProbeSettingsJpaRepository organizations = mock(OrganizationProbeSettingsJpaRepository.class);
        ResourceProbeSettingsJpaRepository resources = mock(ResourceProbeSettingsJpaRepository.class);
        MonitoringSettingsIndex index = new MonitoringSettingsIndex(organizations, resources);
        MonitoringSettingsResolver resolver = new MonitoringSettingsResolver(index, properties());
        index.putOrganization(10, ProbeType.PING, values(false, 41));
        index.putResource(20, ProbeType.PING, values(true, 52));

        assertThat(resolver.resolve(20, 10, ProbeType.PING).intervalSeconds()).isEqualTo(52);
        index.removeResource(20, ProbeType.PING);
        assertThat(resolver.resolve(20, 10, ProbeType.PING).intervalSeconds()).isEqualTo(41);
        index.removeOrganization(10, ProbeType.PING);
        assertThat(resolver.resolve(20, 10, ProbeType.PING).source()).isEqualTo(SettingsSource.APPLICATION);
    }

    private ProbeSettingsValues values(boolean enabled, int interval) {
        return new ProbeSettingsValues(enabled, interval, 3, 2, StorageMode.LATEST_ONLY, null, 500);
    }

    private MonitoringProperties properties() {
        var defaults = new EnumMap<ProbeType, DefaultProbeSettings>(ProbeType.class);
        for (ProbeType type : ProbeType.values()) {
            defaults.put(
                    type,
                    new DefaultProbeSettings(
                            true, 30, 3, 2, StorageMode.LATEST_ONLY, null, type == ProbeType.DNS_CHECK ? null : 500));
        }
        return new MonitoringProperties(true, Duration.ofSeconds(5), 20, 30, Duration.ofHours(1), 100, defaults);
    }
}
