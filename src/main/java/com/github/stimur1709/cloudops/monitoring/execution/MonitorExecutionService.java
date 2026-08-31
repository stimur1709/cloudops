package com.github.stimur1709.cloudops.monitoring.execution;

import com.github.stimur1709.cloudops.probe.execution.ProbeExecutionContext;
import com.github.stimur1709.cloudops.probe.execution.ProbeExecutionResult;
import com.github.stimur1709.cloudops.probe.execution.ProbeHandler;
import com.github.stimur1709.cloudops.probe.execution.ProbeHandlerRegistry;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class MonitorExecutionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(MonitorExecutionService.class);

    private final MonitorExecutionPersistenceService persistenceService;
    private final ProbeHandlerRegistry handlerRegistry;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public MonitorExecutionService(
            MonitorExecutionPersistenceService persistenceService,
            ProbeHandlerRegistry handlerRegistry,
            ObjectMapper objectMapper,
            Clock clock) {
        this.persistenceService = persistenceService;
        this.handlerRegistry = handlerRegistry;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public void execute(long monitorId) {
        try {
            MonitorExecutionContext monitor = persistenceService.loadIfExecutable(monitorId);
            if (monitor == null) {
                return;
            }
            ProbeHandler handler = handlerRegistry.get(monitor.type());
            ProbeExecutionResult executionResult = handler.execute(new ProbeExecutionContext(
                    monitor.resourceId(),
                    monitor.type(),
                    monitor.resourceConfig(),
                    monitor.settings().timeoutMs() == null
                            ? 0
                            : monitor.settings().timeoutMs()));
            Instant checkedAt = clock.instant();
            JsonNode result = objectMapper.valueToTree(executionResult);
            persistenceService.saveResult(monitorId, checkedAt, result, executionResult.success());
        } catch (RuntimeException exception) {
            LOGGER.error("event=monitor_execution_failed monitorId={}", monitorId, exception);
        }
    }
}
