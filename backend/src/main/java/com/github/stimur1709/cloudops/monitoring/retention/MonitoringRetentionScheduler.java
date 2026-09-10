package com.github.stimur1709.cloudops.monitoring.retention;

import com.github.stimur1709.cloudops.monitoring.config.MonitoringProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class MonitoringRetentionScheduler {

    private final MonitoringRetentionService retentionService;
    private final MonitoringProperties properties;

    public MonitoringRetentionScheduler(MonitoringRetentionService retentionService, MonitoringProperties properties) {
        this.retentionService = retentionService;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${cloudops.monitoring.retention-poll-interval:1h}")
    public void cleanup() {
        if (!properties.schedulerEnabled()) {
            return;
        }
        retentionService.deleteExpiredBatch();
    }
}
