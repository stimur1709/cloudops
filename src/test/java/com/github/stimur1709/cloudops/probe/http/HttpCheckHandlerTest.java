package com.github.stimur1709.cloudops.probe.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.github.stimur1709.cloudops.probe.ProbeType;
import com.github.stimur1709.cloudops.probe.execution.ProbeExecutionContext;
import com.github.stimur1709.cloudops.probe.execution.ProbeExecutionResult;
import com.github.stimur1709.cloudops.resource.config.ServiceResourceConfig;
import org.junit.jupiter.api.Test;

class HttpCheckHandlerTest {

    @Test
    void executesHttpCheckAndReturnsTypedResult() {
        HttpCheckClient client = mock(HttpCheckClient.class);
        ServiceResourceConfig config = new ServiceResourceConfig("https://example.com", 204, 1000);
        HttpCheckResult checkResult = new HttpCheckResult("https://example.com", 204, 204, 10, true);
        when(client.execute(config)).thenReturn(HttpCheckOutcome.completed(checkResult));
        HttpCheckHandler handler = new HttpCheckHandler(client);

        ProbeExecutionResult result = handler.execute(
                new ProbeExecutionContext(2, ProbeType.HTTP_CHECK, config)
        );

        assertThat(handler.type()).isEqualTo(ProbeType.HTTP_CHECK);
        assertThat(handler.isCompatibleWith(config)).isTrue();
        assertThat(result).isEqualTo(ProbeExecutionResult.completed(true, checkResult));
        verify(client).execute(config);
    }
}
