package com.github.stimur1709.cloudops.task.execution;

import com.github.stimur1709.cloudops.task.application.TaskPersistenceService;
import com.github.stimur1709.cloudops.task.config.TaskLeaseProperties;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class TaskLeaseManager {

    private static final Logger log = LoggerFactory.getLogger(TaskLeaseManager.class);

    private final Map<Long, UUID> activeExecutions = new ConcurrentHashMap<>();
    private final TaskPersistenceService persistenceService;
    private final TaskLeaseProperties properties;

    public TaskLeaseManager(TaskPersistenceService persistenceService, TaskLeaseProperties properties) {
        this.persistenceService = persistenceService;
        this.properties = properties;
    }

    public void register(long taskId, UUID executionId) {
        if (properties.enabled()) {
            activeExecutions.put(taskId, executionId);
        }
    }

    public void unregister(long taskId, UUID executionId) {
        activeExecutions.remove(taskId, executionId);
    }

    @Scheduled(fixedDelayString = "${cloudops.task.lease.heartbeat-interval:10s}")
    void renewActiveLeases() {
        if (!properties.enabled()) {
            return;
        }
        activeExecutions.forEach(this::renew);
    }

    private void renew(long taskId, UUID executionId) {
        try {
            if (!persistenceService.renewLease(taskId, executionId)) {
                activeExecutions.remove(taskId, executionId);
                log.warn("event=task_lease_lost taskId={} executionId={}", taskId, executionId);
            }
        } catch (RuntimeException exception) {
            log.error("event=task_lease_renewal_failed taskId={} executionId={}", taskId, executionId, exception);
        }
    }
}
