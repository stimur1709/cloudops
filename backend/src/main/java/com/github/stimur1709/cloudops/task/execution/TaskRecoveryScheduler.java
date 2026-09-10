package com.github.stimur1709.cloudops.task.execution;

import com.github.stimur1709.cloudops.task.config.TaskLeaseProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
class TaskRecoveryScheduler {

    private static final Logger log = LoggerFactory.getLogger(TaskRecoveryScheduler.class);

    private final TaskRecoveryService recoveryService;
    private final TaskLeaseProperties properties;

    TaskRecoveryScheduler(TaskRecoveryService recoveryService, TaskLeaseProperties properties) {
        this.recoveryService = recoveryService;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${cloudops.task.lease.recovery-poll-interval:15s}")
    void recoverExpired() {
        if (!properties.enabled()) {
            return;
        }
        try {
            recoveryService.recoverExpired();
        } catch (RuntimeException exception) {
            log.error("event=task_recovery_failed", exception);
        }
    }
}
