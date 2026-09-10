package com.github.stimur1709.cloudops.probe.tls;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.github.stimur1709.cloudops.probe.ProbeType;
import com.github.stimur1709.cloudops.probe.execution.ProbeExecutionContext;
import com.github.stimur1709.cloudops.probe.execution.ProbeExecutionResult;
import com.github.stimur1709.cloudops.resource.config.DatabaseResourceConfig;
import com.github.stimur1709.cloudops.resource.config.NetworkDeviceResourceConfig;
import com.github.stimur1709.cloudops.resource.config.OtherResourceConfig;
import com.github.stimur1709.cloudops.resource.config.ServerResourceConfig;
import com.github.stimur1709.cloudops.resource.config.ServiceResourceConfig;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class TlsCheckHandlerTest {

    private final TlsCheckClient client = mock(TlsCheckClient.class);
    private final TlsCheckHandler handler = new TlsCheckHandler(client);

    @Test
    void supportsOnlyResourcesWithAnUnambiguousTlsEndpoint() {
        assertThat(handler.isCompatibleWith(new ServerResourceConfig("server", 443)))
                .isTrue();
        assertThat(handler.isCompatibleWith(new ServerResourceConfig("server", null)))
                .isFalse();
        assertThat(handler.isCompatibleWith(new NetworkDeviceResourceConfig("switch", 443)))
                .isTrue();
        assertThat(handler.isCompatibleWith(new NetworkDeviceResourceConfig("switch", null)))
                .isFalse();
        assertThat(handler.isCompatibleWith(new DatabaseResourceConfig("database", 5432, "db")))
                .isTrue();
        assertThat(handler.isCompatibleWith(new ServiceResourceConfig("https://api.local/path", 200)))
                .isTrue();
        assertThat(handler.isCompatibleWith(new ServiceResourceConfig("http://api.local/path", 200)))
                .isFalse();
        assertThat(handler.isCompatibleWith(new OtherResourceConfig())).isFalse();
    }

    @Test
    void httpsServiceUsesExplicitAndDefaultPorts() {
        TlsCheckResult checkResult = new TlsCheckResult(
                "api.local",
                8443,
                5,
                "CN=api.local",
                "CN=CA",
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2027-01-01T00:00:00Z"),
                365);
        when(client.execute("api.local", 8443, 5000)).thenReturn(TlsCheckOutcome.completed(checkResult));

        ProbeExecutionResult result = handler.execute(new ProbeExecutionContext(
                1, ProbeType.TLS_CHECK, new ServiceResourceConfig("https://api.local:8443/path", 200)));

        assertThat(result).isEqualTo(ProbeExecutionResult.completed(true, checkResult));
        verify(client).execute("api.local", 8443, 5000);
        assertThat(handler.isCompatibleWith(new ServiceResourceConfig("https://api.local", 200)))
                .isTrue();
    }
}
