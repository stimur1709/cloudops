package com.github.stimur1709.cloudops.probe.port;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.stimur1709.cloudops.probe.ProbeErrorCode;
import java.net.ConnectException;
import java.net.ServerSocket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class PortCheckClientTest {

    @Test
    void connectsToLocalTcpServerAndReportsResponseTime() throws Exception {
        try (ServerSocket server = new ServerSocket(0)) {
            PortCheckOutcome outcome =
                    new PortCheckClient(Duration.ofSeconds(1)).execute("127.0.0.1", server.getLocalPort());

            assertThat(outcome.completed()).isTrue();
            assertThat(outcome.result().host()).isEqualTo("127.0.0.1");
            assertThat(outcome.result().port()).isEqualTo(server.getLocalPort());
            assertThat(outcome.result().responseTimeMs()).isGreaterThanOrEqualTo(0);
        }
    }

    @Test
    void classifiesConnectionRefusedTimeoutAndDnsErrors() {
        assertFailure(new ConnectException(), ProbeErrorCode.CONNECTION_ERROR);
        assertFailure(new SocketTimeoutException(), ProbeErrorCode.TIMEOUT);
        assertFailure(new UnknownHostException(), ProbeErrorCode.DNS_ERROR);
    }

    private void assertFailure(java.io.IOException exception, ProbeErrorCode expectedCode) {
        PortCheckClient client = new PortCheckClient(Duration.ofSeconds(1), (host, port, timeout) -> {
            throw exception;
        });

        PortCheckOutcome outcome = client.execute("host", 1234);

        assertThat(outcome.completed()).isFalse();
        assertThat(outcome.errorCode()).isEqualTo(expectedCode);
        assertThat(outcome.errorMessage()).isNotBlank();
    }
}
