package com.github.stimur1709.cloudops.monitoring.settings.application;

import com.github.stimur1709.cloudops.probe.ProbeType;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Serializes local post-commit updates and retries using current database values, never captured settings. */
@Component
public class MonitoringSettingsRecovery {

    private static final Logger LOGGER = LoggerFactory.getLogger(MonitoringSettingsRecovery.class);

    private final MonitoringSettingsSynchronizer synchronizer;
    private final Set<PendingSettings> pending = new LinkedHashSet<>();

    public MonitoringSettingsRecovery(MonitoringSettingsSynchronizer synchronizer) {
        this.synchronizer = synchronizer;
    }

    public synchronized void organizationCommitted(long id, ProbeType type) {
        synchronize(new PendingSettings(true, id, type), false);
    }

    public synchronized void resourceCommitted(long id, ProbeType type) {
        synchronize(new PendingSettings(false, id, type), false);
    }

    @Scheduled(fixedDelayString = "${cloudops.monitoring.settings-recovery-interval:30s}")
    public synchronized void retryPending() {
        for (var settings : List.copyOf(pending)) {
            synchronize(settings, true);
        }
    }

    private void synchronize(PendingSettings settings, boolean recovery) {
        pending.add(settings);
        try {
            if (settings.organization()) {
                synchronizer.synchronizeOrganization(settings.id(), settings.type(), recovery);
            } else {
                synchronizer.synchronizeResource(settings.id(), settings.type(), recovery);
            }
            pending.remove(settings);
            if (recovery) {
                LOGGER.info("event=monitoring_settings_recovered settings={}", settings);
            }
        } catch (RuntimeException exception) {
            // Application boundary: the settings mutation has already committed. Keep the key for retry.
            LOGGER.error(
                    "event=monitoring_settings_synchronization_failed settings={} recovery={}",
                    settings,
                    recovery,
                    exception);
        }
    }
}
