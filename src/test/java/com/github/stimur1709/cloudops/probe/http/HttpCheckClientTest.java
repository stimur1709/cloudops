package com.github.stimur1709.cloudops.probe.http;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.stimur1709.cloudops.probe.ProbeErrorCode;
import java.io.IOException;
import java.net.ConnectException;
import java.net.UnknownHostException;
import javax.net.ssl.SSLException;
import org.junit.jupiter.api.Test;

class HttpCheckClientTest {

    private final HttpCheckClient client = new HttpCheckClient();

    @Test
    void classifiesDnsConnectionTlsAndGenericClientErrors() {
        assertFailure(new UnknownHostException(), ProbeErrorCode.DNS_ERROR);
        assertFailure(new ConnectException(), ProbeErrorCode.CONNECTION_ERROR);
        assertFailure(new SSLException("TLS"), ProbeErrorCode.TLS_ERROR);
        assertFailure(new IOException("other"), ProbeErrorCode.HTTP_CLIENT_ERROR);
    }

    private void assertFailure(IOException cause, ProbeErrorCode expectedCode) {
        HttpCheckOutcome outcome = client.classify(new IOException(cause));

        assertThat(outcome.completed()).isFalse();
        assertThat(outcome.errorCode()).isEqualTo(expectedCode);
        assertThat(outcome.errorMessage()).isNotBlank();
    }
}
