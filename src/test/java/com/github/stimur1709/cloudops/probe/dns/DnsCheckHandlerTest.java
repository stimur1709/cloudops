package com.github.stimur1709.cloudops.probe.dns;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import com.github.stimur1709.cloudops.probe.ProbeType;
import com.github.stimur1709.cloudops.probe.execution.ProbeExecutionContext;
import com.github.stimur1709.cloudops.probe.execution.ProbeExecutionResult;
import com.github.stimur1709.cloudops.resource.config.DatabaseResourceConfig;
import com.github.stimur1709.cloudops.resource.config.OtherResourceConfig;
import com.github.stimur1709.cloudops.resource.config.ServerResourceConfig;
import com.github.stimur1709.cloudops.resource.config.ServiceResourceConfig;
import org.junit.jupiter.api.Test;

class DnsCheckHandlerTest {

    private final DnsCheckClient client = mock(DnsCheckClient.class);
    private final DnsCheckHandler handler = new DnsCheckHandler(client);

    @Test
    void supportsHostnamesButRejectsIpLiteralsAndOtherResources() {
        assertThat(handler.isCompatibleWith(new ServerResourceConfig("server.local", null))).isTrue();
        assertThat(handler.isCompatibleWith(new DatabaseResourceConfig("db.local", 5432, "db"))).isTrue();
        assertThat(handler.isCompatibleWith(new ServiceResourceConfig("https://api.local/path", 200, 1000))).isTrue();
        assertThat(handler.isCompatibleWith(new ServerResourceConfig("192.0.2.1", null))).isFalse();
        assertThat(handler.isCompatibleWith(new ServerResourceConfig("2001:db8::1", null))).isFalse();
        assertThat(handler.isCompatibleWith(new ServiceResourceConfig("https://[2001:db8::1]", 200, 1000))).isFalse();
        assertThat(handler.isCompatibleWith(new OtherResourceConfig())).isFalse();
    }

    @Test
    void serviceUsesHostnameFromUrl() {
        DnsCheckResult checkResult = new DnsCheckResult("api.local", List.of("127.0.0.1"), 2);
        when(client.execute("api.local")).thenReturn(DnsCheckOutcome.completed(checkResult));

        ProbeExecutionResult result = handler.execute(new ProbeExecutionContext(
                1, ProbeType.DNS_CHECK, new ServiceResourceConfig("https://api.local/status", 200, 1000)
        ));

        assertThat(result).isEqualTo(ProbeExecutionResult.completed(true, checkResult));
        verify(client).execute("api.local");
    }
}
