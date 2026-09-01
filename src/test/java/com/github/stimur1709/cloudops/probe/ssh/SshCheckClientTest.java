package com.github.stimur1709.cloudops.probe.ssh;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.stimur1709.cloudops.credential.application.ResolvedSshPrivateKey;
import com.github.stimur1709.cloudops.credential.application.ResolvedUsernamePassword;
import com.github.stimur1709.cloudops.probe.ProbeErrorCode;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import org.apache.sshd.common.config.keys.PublicKeyEntry;
import org.apache.sshd.common.keyprovider.KeyPairProvider;
import org.apache.sshd.server.SshServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SshCheckClientTest {

    @TempDir
    private Path temporaryDirectory;

    @Test
    void acceptsUnknownHostKeyAndAuthenticatesWithPasswordWithoutExecutingACommand() throws Exception {
        KeyPair hostKey = keyPair();
        try (LocalSshServer server = server(hostKey, null)) {
            SshCheckOutcome outcome = new SshCheckClient(new SshProperties(
                            SshHostKeyVerification.ACCEPT_ALL, temporaryDirectory.resolve("missing-known-hosts")))
                    .execute(
                            "127.0.0.1",
                            server.port(),
                            new ResolvedUsernamePassword("cloudops", "correct-password"),
                            3000);

            assertThat(outcome.completed()).as(outcome.toString()).isTrue();
            assertThat(outcome.result().username()).isEqualTo("cloudops");
            assertThat(outcome.result().authMethod()).isEqualTo(SshAuthMethod.PASSWORD);
            assertThat(outcome.result().serverVersion()).contains("APACHE-SSHD");
            assertThat(outcome.result().toString()).doesNotContain("correct-password");
        }
    }

    @Test
    void authenticatesWithPrivateKeyWithoutReturningPrivateMaterial() throws Exception {
        KeyPair hostKey = keyPair();
        KeyPair userKey = keyPair();
        String privateKey = pem(userKey);
        try (LocalSshServer server = server(hostKey, userKey)) {
            SshCheckOutcome outcome = new SshCheckClient(new SshProperties(
                            SshHostKeyVerification.ACCEPT_ALL, temporaryDirectory.resolve("missing-known-hosts")))
                    .execute("127.0.0.1", server.port(), new ResolvedSshPrivateKey("cloudops", privateKey), 3000);

            assertThat(outcome.completed()).as(outcome.toString()).isTrue();
            assertThat(outcome.result().authMethod()).isEqualTo(SshAuthMethod.PUBLIC_KEY);
            assertThat(outcome.result().toString()).doesNotContain("PRIVATE KEY");
        }
    }

    @Test
    void distinguishesAuthenticationAndHostKeyFailures() throws Exception {
        KeyPair hostKey = keyPair();
        try (LocalSshServer server = server(hostKey, null)) {
            SshCheckClient client = new SshCheckClient(
                    new SshProperties(SshHostKeyVerification.KNOWN_HOSTS, knownHosts(server.port(), hostKey)));
            SshCheckOutcome authentication = client.execute(
                    "127.0.0.1", server.port(), new ResolvedUsernamePassword("cloudops", "wrong-password"), 3000);
            Files.writeString(temporaryDirectory.resolve("untrusted-known-hosts"), "", StandardCharsets.UTF_8);
            SshCheckOutcome hostKeyFailure = new SshCheckClient(new SshProperties(
                            SshHostKeyVerification.KNOWN_HOSTS, temporaryDirectory.resolve("untrusted-known-hosts")))
                    .execute(
                            "127.0.0.1",
                            server.port(),
                            new ResolvedUsernamePassword("cloudops", "correct-password"),
                            3000);

            assertThat(authentication.errorCode()).isEqualTo(ProbeErrorCode.SSH_AUTHENTICATION_ERROR);
            assertThat(hostKeyFailure.errorCode()).isEqualTo(ProbeErrorCode.SSH_HOST_KEY_ERROR);
        }
    }

    @Test
    void classifiesNetworkAndProtocolFailures() {
        assertFailure(new UnknownHostException(), ProbeErrorCode.DNS_ERROR);
        assertFailure(new SocketTimeoutException(), ProbeErrorCode.TIMEOUT);
        assertFailure(new ConnectException(), ProbeErrorCode.CONNECTION_ERROR);
        assertFailure(
                new SshCheckClient.SshCheckException(
                        ProbeErrorCode.SSH_HANDSHAKE_ERROR, "SSH handshake failed", new Exception()),
                ProbeErrorCode.SSH_HANDSHAKE_ERROR);
        assertFailure(
                new SshCheckClient.SshCheckException(
                        ProbeErrorCode.CREDENTIAL_ERROR, "SSH credential is invalid", new Exception()),
                ProbeErrorCode.CREDENTIAL_ERROR);
    }

    private void assertFailure(Exception failure, ProbeErrorCode expectedCode) {
        SshCheckClient client = new SshCheckClient(
                SshHostKeyVerification.KNOWN_HOSTS,
                temporaryDirectory.resolve("known-hosts"),
                (host, port, credential, timeout, hostKeyVerification, knownHosts) -> {
                    if (failure instanceof java.io.IOException ioException) {
                        throw ioException;
                    }
                    throw new IllegalStateException(failure);
                });

        SshCheckOutcome outcome =
                client.execute("host", 22, new ResolvedUsernamePassword("username", "top-secret"), 100);

        assertThat(outcome.errorCode()).isEqualTo(expectedCode);
        assertThat(outcome.errorMessage()).doesNotContain("top-secret");
    }

    private LocalSshServer server(KeyPair hostKey, KeyPair acceptedUserKey) throws Exception {
        SshServer server = SshServer.setUpDefaultServer();
        server.setHost("127.0.0.1");
        server.setPort(0);
        server.setKeyPairProvider(KeyPairProvider.wrap(hostKey));
        server.setPasswordAuthenticator(
                (username, password, session) -> username.equals("cloudops") && password.equals("correct-password"));
        server.setPublickeyAuthenticator((username, key, session) ->
                username.equals("cloudops") && acceptedUserKey != null && key.equals(acceptedUserKey.getPublic()));
        server.start();
        return new LocalSshServer(server);
    }

    private Path knownHosts(int port, KeyPair hostKey) throws Exception {
        Path path = temporaryDirectory.resolve("known-hosts-" + port);
        Files.writeString(
                path,
                "[127.0.0.1]:" + port + " " + PublicKeyEntry.toString(hostKey.getPublic()) + System.lineSeparator(),
                StandardCharsets.UTF_8);
        return path;
    }

    private KeyPair keyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    private String pem(KeyPair keyPair) {
        String encoded = Base64.getMimeEncoder(64, new byte[] {'\n'})
                .encodeToString(keyPair.getPrivate().getEncoded());
        return "-----BEGIN PRIVATE KEY-----\n" + encoded + "\n-----END PRIVATE KEY-----\n";
    }

    private record LocalSshServer(SshServer server) implements AutoCloseable {
        int port() {
            return server.getPort();
        }

        @Override
        public void close() throws Exception {
            server.stop(true);
        }
    }
}
