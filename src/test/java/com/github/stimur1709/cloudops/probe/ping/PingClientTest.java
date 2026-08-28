package com.github.stimur1709.cloudops.probe.ping;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.UnknownHostException;
import java.time.Duration;

import com.github.stimur1709.cloudops.probe.ProbeErrorCode;
import org.junit.jupiter.api.Test;

class PingClientTest {

    @Test
    void reachesLocalHostAndReportsResponseTime() {
        PingOutcome outcome = new PingClient(Duration.ofSeconds(1), (host, timeout) -> true)
                .execute("127.0.0.1");

        assertThat(outcome.completed()).isTrue();
        assertThat(outcome.result().host()).isEqualTo("127.0.0.1");
        assertThat(outcome.result().responseTimeMs()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void classifiesTimeoutDnsAndConnectionFailures() {
        assertFailure((host, timeout) -> false, ProbeErrorCode.TIMEOUT);
        assertFailure((host, timeout) -> {
            throw new UnknownHostException(host);
        }, ProbeErrorCode.DNS_ERROR);
        assertFailure((host, timeout) -> {
            throw new IOException("network");
        }, ProbeErrorCode.CONNECTION_ERROR);
    }

    private void assertFailure(PingClient.Reachability reachability, ProbeErrorCode code) {
        PingOutcome outcome = new PingClient(Duration.ofMillis(10), reachability).execute("host");

        assertThat(outcome.completed()).isFalse();
        assertThat(outcome.errorCode()).isEqualTo(code);
    }
}
