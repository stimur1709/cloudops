package com.github.stimur1709.cloudops.probe.port;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import com.github.stimur1709.cloudops.probe.ProbeType;
import com.github.stimur1709.cloudops.probe.execution.ProbeExecutionContext;
import com.github.stimur1709.cloudops.probe.execution.ProbeExecutionResult;
import com.github.stimur1709.cloudops.probe.execution.ProbeHandlerRegistry;
import com.github.stimur1709.cloudops.probe.http.HttpCheckClient;
import com.github.stimur1709.cloudops.probe.http.HttpCheckHandler;
import com.github.stimur1709.cloudops.resource.config.DatabaseResourceConfig;
import com.github.stimur1709.cloudops.resource.config.NetworkDeviceResourceConfig;
import com.github.stimur1709.cloudops.resource.config.OtherResourceConfig;
import com.github.stimur1709.cloudops.resource.config.ServerResourceConfig;
import com.github.stimur1709.cloudops.resource.config.ServiceResourceConfig;
import org.junit.jupiter.api.Test;

class PortCheckHandlerTest {

    private final PortCheckClient client = mock(PortCheckClient.class);
    private final PortCheckHandler handler = new PortCheckHandler(client);

    @Test
    void supportsOnlyConfigsWithAHostAndPort() {
        assertThat(handler.isCompatibleWith(new ServerResourceConfig("server", 22))).isTrue();
        assertThat(handler.isCompatibleWith(new ServerResourceConfig("server", null))).isFalse();
        assertThat(handler.isCompatibleWith(new NetworkDeviceResourceConfig("switch", 161))).isTrue();
        assertThat(handler.isCompatibleWith(new NetworkDeviceResourceConfig("switch", null))).isFalse();
        assertThat(handler.isCompatibleWith(new DatabaseResourceConfig("database", 5432, "cloudops"))).isTrue();
        assertThat(handler.isCompatibleWith(new ServiceResourceConfig("https://example.com", 200, 1000))).isFalse();
        assertThat(handler.isCompatibleWith(new OtherResourceConfig())).isFalse();
    }

    @Test
    void mapsClientResultAndUsesResourceEndpoint() {
        PortCheckResult checkResult = new PortCheckResult("database", 5432, 18);
        when(client.execute("database", 5432)).thenReturn(PortCheckOutcome.completed(checkResult));

        ProbeExecutionResult result = handler.execute(new ProbeExecutionContext(
                1, ProbeType.PORT_CHECK, new DatabaseResourceConfig("database", 5432, "cloudops")
        ));

        assertThat(handler.type()).isEqualTo(ProbeType.PORT_CHECK);
        assertThat(result).isEqualTo(ProbeExecutionResult.completed(true, checkResult));
        verify(client).execute("database", 5432);
    }

    @Test
    void registryProvidesTheSamePortHandlerToEveryCaller() {
        ProbeHandlerRegistry registry = new ProbeHandlerRegistry(List.of(
                new HttpCheckHandler(mock(HttpCheckClient.class)), handler
        ));

        assertThat(registry.get(ProbeType.PORT_CHECK)).isSameAs(handler);
        assertThat(registry.supports(ProbeType.PORT_CHECK, new ServerResourceConfig("server", 22))).isTrue();
        assertThatThrownBy(() -> handler.execute(new ProbeExecutionContext(
                1, ProbeType.PORT_CHECK, new OtherResourceConfig()
        ))).isInstanceOf(IllegalArgumentException.class);
    }
}
