package com.github.stimur1709.cloudops.monitoring.execution;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import com.github.stimur1709.cloudops.probe.ProbeType;
import com.github.stimur1709.cloudops.probe.execution.ProbeExecutionResult;
import com.github.stimur1709.cloudops.probe.execution.ProbeHandler;
import com.github.stimur1709.cloudops.probe.execution.ProbeHandlerRegistry;
import com.github.stimur1709.cloudops.resource.config.ServiceResourceConfig;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class MonitorExecutionServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-27T12:00:00Z");

    @Test
    void resolvesHandlerThroughSharedRegistryAndPersistsUniversalResult() {
        MonitorExecutionPersistenceService persistence = mock(MonitorExecutionPersistenceService.class);
        ProbeHandlerRegistry registry = mock(ProbeHandlerRegistry.class);
        ProbeHandler handler = mock(ProbeHandler.class);
        ServiceResourceConfig config = new ServiceResourceConfig("https://example.com", 200, 1000);
        when(persistence.loadIfExecutable(7)).thenReturn(new MonitorExecutionContext(
                7, 8, ProbeType.HTTP_CHECK, config
        ));
        when(registry.get(ProbeType.HTTP_CHECK)).thenReturn(handler);
        when(handler.execute(any())).thenReturn(ProbeExecutionResult.completed(true, "ok"));
        MonitorExecutionService service = new MonitorExecutionService(
                persistence, registry, new ObjectMapper(), Clock.fixed(NOW, ZoneOffset.UTC)
        );

        service.execute(7);

        verify(registry).get(ProbeType.HTTP_CHECK);
        verify(handler).execute(any());
        verify(persistence).saveResult(eq(7L), eq(NOW), any());
    }

    @Test
    void doesNotPersistInternalExecutionFailure() {
        MonitorExecutionPersistenceService persistence = mock(MonitorExecutionPersistenceService.class);
        ProbeHandlerRegistry registry = mock(ProbeHandlerRegistry.class);
        ProbeHandler handler = mock(ProbeHandler.class);
        when(persistence.loadIfExecutable(7)).thenReturn(new MonitorExecutionContext(
                7, 8, ProbeType.HTTP_CHECK,
                new ServiceResourceConfig("https://example.com", 200, 1000)
        ));
        when(registry.get(ProbeType.HTTP_CHECK)).thenReturn(handler);
        when(handler.execute(any())).thenThrow(new IllegalStateException("internal failure"));
        MonitorExecutionService service = new MonitorExecutionService(
                persistence, registry, new ObjectMapper(), Clock.fixed(NOW, ZoneOffset.UTC)
        );

        service.execute(7);

        verify(persistence, never()).saveResult(anyLong(), any(), any());
    }
}
