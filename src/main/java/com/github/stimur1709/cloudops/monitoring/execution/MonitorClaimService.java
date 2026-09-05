package com.github.stimur1709.cloudops.monitoring.execution;

import com.github.stimur1709.cloudops.monitoring.config.MonitoringProperties;
import com.github.stimur1709.cloudops.monitoring.settings.MonitoringSettingsResolver;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MonitorClaimService {

    private final MonitorScheduleRepository scheduleRepository;
    private final MonitoringSettingsResolver settingsResolver;
    private final MonitoringProperties properties;
    private final Clock clock;

    public MonitorClaimService(
            MonitorScheduleRepository scheduleRepository,
            MonitoringSettingsResolver settingsResolver,
            MonitoringProperties properties,
            Clock clock) {
        this.scheduleRepository = scheduleRepository;
        this.settingsResolver = settingsResolver;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public List<Long> claimDue() {
        var now = clock.instant();
        var claimed = new ArrayList<>(scheduleRepository.claimRequested(properties.batchSize()));
        for (var monitor : claimed) {
            reserve(monitor, now);
        }
        int remaining = properties.batchSize() - claimed.size();
        if (remaining > 0) {
            var periodic = scheduleRepository.claimDue(now, remaining);
            periodic.forEach(monitor -> reserve(monitor, now));
            claimed.addAll(periodic);
        }
        return claimed.stream().map(ClaimedMonitor::id).toList();
    }

    private void reserve(ClaimedMonitor monitor, Instant now) {
        Instant nextRunAt = monitor.nextRunAt();
        if (!nextRunAt.isAfter(now)) {
            var settings = settingsResolver.resolve(monitor.resourceId(), monitor.organizationId(), monitor.type());
            nextRunAt = now.plusSeconds(settings.intervalSeconds());
        }
        scheduleRepository.scheduleNext(monitor.id(), nextRunAt);
    }
}
