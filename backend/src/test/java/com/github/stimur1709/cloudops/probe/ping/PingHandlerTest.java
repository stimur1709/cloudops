package com.github.stimur1709.cloudops.probe.ping;

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
import org.junit.jupiter.api.Test;

class PingHandlerTest {

    private final PingClient client = mock(PingClient.class);
    private final PingHandler handler = new PingHandler(client);

    @Test
    void supportsEveryResourceWithAHost() {
        assertThat(handler.isCompatibleWith(new ServerResourceConfig("server", null)))
                .isTrue();
        assertThat(handler.isCompatibleWith(new NetworkDeviceResourceConfig("switch", null)))
                .isTrue();
        assertThat(handler.isCompatibleWith(new DatabaseResourceConfig("database", 5432, "db")))
                .isTrue();
        assertThat(handler.isCompatibleWith(new ServiceResourceConfig("http://127.0.0.1/path", 200)))
                .isTrue();
        assertThat(handler.isCompatibleWith(new OtherResourceConfig())).isFalse();
    }

    @Test
    void serviceUsesHostFromUrl() {
        PingResult checkResult = new PingResult("127.0.0.1", 1);
        when(client.execute("127.0.0.1", 5000)).thenReturn(PingOutcome.completed(checkResult));

        ProbeExecutionResult result = handler.execute(
                new ProbeExecutionContext(1, ProbeType.PING, new ServiceResourceConfig("http://127.0.0.1/path", 200)));

        assertThat(result).isEqualTo(ProbeExecutionResult.completed(true, checkResult));
        verify(client).execute("127.0.0.1", 5000);
    }
}
