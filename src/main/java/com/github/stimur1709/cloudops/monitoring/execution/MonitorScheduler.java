package com.github.stimur1709.cloudops.monitoring.execution;

import com.github.stimur1709.cloudops.monitoring.config.MonitoringProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class MonitorScheduler {

    private final MonitorClaimService claimService;
    private final MonitorExecutionService executionService;
    private final MonitoringProperties properties;

    public MonitorScheduler(
            MonitorClaimService claimService,
            MonitorExecutionService executionService,
            MonitoringProperties properties) {
        this.claimService = claimService;
        this.executionService = executionService;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${cloudops.monitoring.poll-interval:5s}")
    public void poll() {
        if (!properties.schedulerEnabled()) {
            return;
        }
        claimService.claimDue().forEach(executionService::execute);
        claimService.claimRequested().forEach(executionService::execute);
    }
}
