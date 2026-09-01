package com.github.stimur1709.cloudops.monitoring.execution;

import com.github.stimur1709.cloudops.monitoring.config.MonitoringProperties;
import com.github.stimur1709.cloudops.monitoring.settings.MonitoringSettingsResolver;
import java.time.Clock;
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
        var claimed = scheduleRepository.claimDue(now, properties.batchSize());
        for (var monitor : claimed) {
            var settings = settingsResolver.resolve(monitor.resourceId(), monitor.organizationId(), monitor.type());
            scheduleRepository.scheduleNext(monitor.id(), now.plusSeconds(settings.intervalSeconds()));
        }
        return claimed.stream()
                .map(MonitorScheduleRepository.ClaimedMonitor::id)
                .toList();
    }

    @Transactional
    public List<Long> claimRequested() {
        return scheduleRepository.claimRequested(properties.batchSize());
    }
}
