package com.github.stimur1709.cloudops.probe.tls;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.github.stimur1709.cloudops.probe.ProbeErrorCode;
import java.io.IOException;
import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManagerFactory;
import javax.security.auth.x500.X500Principal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TlsCheckClientTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @TempDir
    private Path temporaryDirectory;

    @Test
    void completesHandshakeWithLocalTrustedTlsServer() throws Exception {
        KeyStore keyStore = createKeyStore();
        SSLContext serverContext = serverContext(keyStore);
        SSLContext clientContext = clientContext(keyStore);
        try (SSLServerSocket server =
                        (SSLServerSocket) serverContext.getServerSocketFactory().createServerSocket(0);
                var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<?> handshake = executor.submit(() -> {
                try (SSLSocket socket = (SSLSocket) server.accept()) {
                    socket.startHandshake();
                } catch (IOException exception) {
                    throw new IllegalStateException(exception);
                }
            });

            TlsCheckOutcome outcome = new TlsCheckClient(
                            Duration.ofSeconds(2), Clock.systemUTC(), clientContext.getSocketFactory())
                    .execute("localhost", server.getLocalPort());

            assertThat(outcome.completed()).isTrue();
            assertThat(outcome.result().subject()).contains("CN=localhost");
            assertThat(outcome.result().notAfter()).isAfter(Instant.now());
            handshake.get();
        }
    }

    @Test
    void returnsCertificateDetailsAndExpiry() {
        X509Certificate certificate = mock(X509Certificate.class);
        when(certificate.getSubjectX500Principal()).thenReturn(new X500Principal("CN=api.example.com"));
        when(certificate.getIssuerX500Principal()).thenReturn(new X500Principal("CN=Example CA"));
        when(certificate.getNotBefore()).thenReturn(Date.from(NOW.minus(Duration.ofDays(10))));
        when(certificate.getNotAfter()).thenReturn(Date.from(NOW.plus(Duration.ofDays(125))));
        TlsCheckClient client = client((host, port, timeout) -> certificate);

        TlsCheckOutcome outcome = client.execute("api.example.com", 443);

        assertThat(outcome.completed()).isTrue();
        assertThat(outcome.result().host()).isEqualTo("api.example.com");
        assertThat(outcome.result().port()).isEqualTo(443);
        assertThat(outcome.result().subject()).isEqualTo("CN=api.example.com");
        assertThat(outcome.result().issuer()).isEqualTo("CN=Example CA");
        assertThat(outcome.result().notBefore()).isEqualTo(NOW.minus(Duration.ofDays(10)));
        assertThat(outcome.result().notAfter()).isEqualTo(NOW.plus(Duration.ofDays(125)));
        assertThat(outcome.result().daysUntilExpiry()).isEqualTo(125);
        assertThat(outcome.result().responseTimeMs()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void classifiesDnsTimeoutConnectionAndTlsFailures() {
        assertFailure(new UnknownHostException(), ProbeErrorCode.DNS_ERROR);
        assertFailure(new SocketTimeoutException(), ProbeErrorCode.TIMEOUT);
        assertFailure(new SSLHandshakeException("certificate"), ProbeErrorCode.TLS_ERROR);
        assertFailure(new IOException("connection"), ProbeErrorCode.CONNECTION_ERROR);
    }

    private TlsCheckClient client(TlsCheckClient.Connector connector) {
        return new TlsCheckClient(Duration.ofSeconds(1), Clock.fixed(NOW, ZoneOffset.UTC), connector);
    }

    private void assertFailure(IOException exception, ProbeErrorCode code) {
        TlsCheckOutcome outcome = client((host, port, timeout) -> {
                    throw exception;
                })
                .execute("host", 443);

        assertThat(outcome.completed()).isFalse();
        assertThat(outcome.errorCode()).isEqualTo(code);
    }

    private KeyStore createKeyStore() throws Exception {
        Path keyStorePath = temporaryDirectory.resolve("server.p12");
        Path keytool = Path.of(System.getProperty("java.home"), "bin", executable("keytool"));
        Process process = new ProcessBuilder(
                        keytool.toString(),
                        "-genkeypair",
                        "-alias",
                        "server",
                        "-storetype",
                        "PKCS12",
                        "-keystore",
                        keyStorePath.toString(),
                        "-storepass",
                        "changeit",
                        "-keypass",
                        "changeit",
                        "-dname",
                        "CN=localhost",
                        "-ext",
                        "SAN=dns:localhost,ip:127.0.0.1",
                        "-keyalg",
                        "RSA",
                        "-validity",
                        "3650",
                        "-noprompt")
                .redirectErrorStream(true)
                .start();
        String output = new String(process.getInputStream().readAllBytes());
        assertThat(process.waitFor()).as(output).isZero();

        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        try (InputStream input = Files.newInputStream(keyStorePath)) {
            keyStore.load(input, "changeit".toCharArray());
        }
        return keyStore;
    }

    private SSLContext serverContext(KeyStore keyStore) throws Exception {
        KeyManagerFactory keyManagers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyManagers.init(keyStore, "changeit".toCharArray());
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(keyManagers.getKeyManagers(), null, null);
        return context;
    }

    private SSLContext clientContext(KeyStore keyStore) throws Exception {
        TrustManagerFactory trustManagers = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagers.init(keyStore);
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(null, trustManagers.getTrustManagers(), null);
        return context;
    }

    private String executable(String name) {
        return System.getProperty("os.name").startsWith("Windows") ? name + ".exe" : name;
    }
}
