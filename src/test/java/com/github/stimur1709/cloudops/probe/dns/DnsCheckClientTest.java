package com.github.stimur1709.cloudops.probe.dns;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetAddress;
import java.net.UnknownHostException;

import com.github.stimur1709.cloudops.probe.ProbeErrorCode;
import org.junit.jupiter.api.Test;

class DnsCheckClientTest {

    @Test
    void returnsEveryResolvedAddressAndResponseTime() throws Exception {
        InetAddress first = InetAddress.getByAddress(new byte[]{10, 0, 0, 1});
        InetAddress second = InetAddress.getByAddress(new byte[]{10, 0, 0, 2});
        DnsCheckClient client = new DnsCheckClient(host -> new InetAddress[]{first, second});

        DnsCheckOutcome outcome = client.execute("service.local");

        assertThat(outcome.completed()).isTrue();
        assertThat(outcome.result().hostname()).isEqualTo("service.local");
        assertThat(outcome.result().addresses()).containsExactly("10.0.0.1", "10.0.0.2");
        assertThat(outcome.result().responseTimeMs()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void mapsUnknownHostToDnsError() {
        DnsCheckClient client = new DnsCheckClient(host -> {
            throw new UnknownHostException(host);
        });

        DnsCheckOutcome outcome = client.execute("missing.invalid");

        assertThat(outcome.completed()).isFalse();
        assertThat(outcome.errorCode()).isEqualTo(ProbeErrorCode.DNS_ERROR);
    }
}
