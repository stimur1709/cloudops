package com.github.stimur1709.cloudops.ssh;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.stimur1709.cloudops.credential.application.ResolvedSshPrivateKey;
import com.github.stimur1709.cloudops.credential.application.ResolvedUsernamePassword;
import com.github.stimur1709.cloudops.probe.ssh.SshHostKeyVerification;
import com.github.stimur1709.cloudops.probe.ssh.SshProperties;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Duration;
import java.util.Base64;
import org.apache.sshd.common.keyprovider.KeyPairProvider;
import org.apache.sshd.server.Environment;
import org.apache.sshd.server.ExitCallback;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.channel.ChannelSession;
import org.apache.sshd.server.command.Command;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SshClientTest {

    @TempDir
    private Path temporaryDirectory;

    @Test
    void executesWithPasswordAndReturnsStdoutStderrAndNonZeroExitCode() throws Exception {
        try (LocalSshServer server = server(null)) {
            SshCommandResult result = client().execute(
                            "127.0.0.1",
                            server.port(),
                            new ResolvedUsernamePassword("cloudops", "correct-password"),
                            "result",
                            Duration.ofSeconds(3),
                            1024);

            assertThat(result.exitCode()).isEqualTo(7);
            assertThat(result.stdout()).isEqualTo("standard output\n");
            assertThat(result.stderr()).isEqualTo("standard error\n");
            assertThat(result.outputTruncated()).isFalse();
        }
    }

    @Test
    void executesWithPrivateKeyWithoutExposingSecret() throws Exception {
        KeyPair userKey = keyPair();
        String privateKey = pem(userKey);
        try (LocalSshServer server = server(userKey)) {
            SshCommandResult result = client().execute(
                            "127.0.0.1",
                            server.port(),
                            new ResolvedSshPrivateKey("cloudops", privateKey),
                            "result",
                            Duration.ofSeconds(3),
                            1024);

            assertThat(result.exitCode()).isEqualTo(7);
            assertThat(result.toString()).doesNotContain("PRIVATE KEY");
        }
    }

    @Test
    void drainsBothStreamsButLimitsStoredOutput() throws Exception {
        try (LocalSshServer server = server(null)) {
            SshCommandResult result = client().execute(
                            "127.0.0.1",
                            server.port(),
                            new ResolvedUsernamePassword("cloudops", "correct-password"),
                            "large",
                            Duration.ofSeconds(3),
                            64);

            assertThat(result.stdout().getBytes(StandardCharsets.UTF_8).length
                            + result.stderr().getBytes(StandardCharsets.UTF_8).length)
                    .isEqualTo(64);
            assertThat(result.outputTruncated()).isTrue();
            assertThat(result.exitCode()).isZero();
        }
    }

    @Test
    void closesTimedOutCommandWithControlledError() throws Exception {
        try (LocalSshServer server = server(null)) {
            assertThatThrownBy(() -> client().execute(
                                    "127.0.0.1",
                                    server.port(),
                                    new ResolvedUsernamePassword("cloudops", "correct-password"),
                                    "slow",
                                    Duration.ofMillis(100),
                                    1024))
                    .isInstanceOfSatisfying(SshClientException.class, exception -> {
                        assertThat(exception.type()).isEqualTo(SshErrorType.COMMAND_TIMEOUT);
                        assertThat(exception.retriable()).isFalse();
                    });
        }
    }

    private SshClient client() {
        return new SshClient(
                new SshProperties(SshHostKeyVerification.ACCEPT_ALL, temporaryDirectory.resolve("unused-known-hosts")));
    }

    private LocalSshServer server(KeyPair acceptedUserKey) throws Exception {
        SshServer server = SshServer.setUpDefaultServer();
        server.setHost("127.0.0.1");
        server.setPort(0);
        server.setKeyPairProvider(KeyPairProvider.wrap(keyPair()));
        server.setPasswordAuthenticator(
                (username, password, session) -> username.equals("cloudops") && password.equals("correct-password"));
        server.setPublickeyAuthenticator((username, key, session) ->
                acceptedUserKey != null && username.equals("cloudops") && key.equals(acceptedUserKey.getPublic()));
        server.setCommandFactory((channel, command) -> new TestCommand(command));
        server.start();
        return new LocalSshServer(server);
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

    private static final class TestCommand implements Command {
        private final String command;
        private OutputStream stdout;
        private OutputStream stderr;
        private ExitCallback exitCallback;
        private Thread thread;

        private TestCommand(String command) {
            this.command = command;
        }

        @Override
        public void setInputStream(InputStream inputStream) {}

        @Override
        public void setOutputStream(OutputStream outputStream) {
            this.stdout = outputStream;
        }

        @Override
        public void setErrorStream(OutputStream errorStream) {
            this.stderr = errorStream;
        }

        @Override
        public void setExitCallback(ExitCallback callback) {
            this.exitCallback = callback;
        }

        @Override
        public void start(ChannelSession channel, Environment environment) {
            thread = Thread.ofVirtual().start(() -> {
                try {
                    switch (command) {
                        case "result" -> {
                            stdout.write("standard output\n".getBytes(StandardCharsets.UTF_8));
                            stderr.write("standard error\n".getBytes(StandardCharsets.UTF_8));
                            stdout.flush();
                            stderr.flush();
                            exitCallback.onExit(7);
                        }
                        case "large" -> {
                            stdout.write("a".repeat(200).getBytes(StandardCharsets.UTF_8));
                            stderr.write("b".repeat(200).getBytes(StandardCharsets.UTF_8));
                            stdout.flush();
                            stderr.flush();
                            exitCallback.onExit(0);
                        }
                        case "slow" -> {
                            Thread.sleep(Duration.ofSeconds(5));
                            exitCallback.onExit(0);
                        }
                        default -> exitCallback.onExit(127);
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                } catch (java.io.IOException exception) {
                    exitCallback.onExit(1, "write failed");
                }
            });
        }

        @Override
        public void destroy(ChannelSession channel) {
            if (thread != null) {
                thread.interrupt();
            }
        }
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
