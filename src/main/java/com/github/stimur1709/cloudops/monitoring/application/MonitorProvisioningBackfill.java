package com.github.stimur1709.cloudops.monitoring.application;

import com.github.stimur1709.cloudops.resource.persistence.ResourceJpaRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class MonitorProvisioningBackfill implements ApplicationRunner {

    private final ResourceJpaRepository resourceRepository;
    private final MonitorProvisioningService provisioningService;

    public MonitorProvisioningBackfill(
            ResourceJpaRepository resourceRepository, MonitorProvisioningService provisioningService) {
        this.resourceRepository = resourceRepository;
        this.provisioningService = provisioningService;
    }

    @Override
    public void run(ApplicationArguments args) {
        resourceRepository.findAll().forEach(provisioningService::reconcile);
    }
}
