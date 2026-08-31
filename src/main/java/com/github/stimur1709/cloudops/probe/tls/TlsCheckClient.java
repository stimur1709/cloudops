package com.github.stimur1709.cloudops.probe.tls;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.time.Duration;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import com.github.stimur1709.cloudops.probe.ProbeErrorCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class TlsCheckClient {

    private final Duration timeout;
    private final Clock clock;
    private final Connector connector;

    @Autowired
    public TlsCheckClient(Clock clock) {
        this(Duration.ZERO, clock, TlsCheckClient::connect);
    }

    TlsCheckClient(Duration timeout, Clock clock, Connector connector) {
        this.timeout = timeout;
        this.clock = clock;
        this.connector = connector;
    }

    TlsCheckClient(Duration timeout, Clock clock, SSLSocketFactory socketFactory) {
        this(timeout, clock, (host, port, timeoutMs) -> connect(socketFactory, host, port, timeoutMs));
    }

    TlsCheckOutcome execute(String host, int port) {
        return execute(host, port, Math.toIntExact(timeout.toMillis()));
    }

    TlsCheckOutcome execute(String host, int port, int timeoutMs) {
        long startedAt = System.nanoTime();
        try {
            X509Certificate certificate = connector.connect(host, port, timeoutMs);
            long responseTimeMs = Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
            long daysUntilExpiry = Duration.between(clock.instant(), certificate.getNotAfter().toInstant()).toDays();
            return TlsCheckOutcome.completed(new TlsCheckResult(
                    host,
                    port,
                    responseTimeMs,
                    certificate.getSubjectX500Principal().getName(),
                    certificate.getIssuerX500Principal().getName(),
                    certificate.getNotBefore().toInstant(),
                    certificate.getNotAfter().toInstant(),
                    daysUntilExpiry
            ));
        } catch (UnknownHostException exception) {
            return TlsCheckOutcome.failed(ProbeErrorCode.DNS_ERROR, "Host name could not be resolved");
        } catch (SocketTimeoutException exception) {
            return TlsCheckOutcome.failed(ProbeErrorCode.TIMEOUT, "TLS check timed out");
        } catch (SSLException exception) {
            return TlsCheckOutcome.failed(ProbeErrorCode.TLS_ERROR, "TLS handshake or certificate validation failed");
        } catch (IOException exception) {
            return TlsCheckOutcome.failed(ProbeErrorCode.CONNECTION_ERROR, "TLS connection could not be established");
        }
    }

    private static X509Certificate connect(String host, int port, int timeoutMs) throws IOException {
        SSLSocketFactory socketFactory = (SSLSocketFactory) SSLSocketFactory.getDefault();
        return connect(socketFactory, host, port, timeoutMs);
    }

    private static X509Certificate connect(
            SSLSocketFactory socketFactory, String host, int port, int timeoutMs
    ) throws IOException {
        try (SSLSocket socket = (SSLSocket) socketFactory.createSocket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            socket.setSoTimeout(timeoutMs);
            SSLParameters parameters = socket.getSSLParameters();
            parameters.setEndpointIdentificationAlgorithm("HTTPS");
            socket.setSSLParameters(parameters);
            socket.startHandshake();
            Certificate certificate = socket.getSession().getPeerCertificates()[0];
            if (certificate instanceof X509Certificate x509Certificate) {
                return x509Certificate;
            }
            throw new SSLException("Peer did not provide an X.509 certificate");
        }
    }

    @FunctionalInterface
    interface Connector {
        X509Certificate connect(String host, int port, int timeoutMs) throws IOException;
    }
}
