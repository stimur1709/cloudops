package com.github.stimur1709.cloudops.ssh;

import com.github.stimur1709.cloudops.credential.application.ResolvedCredential;
import com.github.stimur1709.cloudops.credential.application.ResolvedSshPrivateKey;
import com.github.stimur1709.cloudops.credential.application.ResolvedUsernamePassword;
import com.github.stimur1709.cloudops.probe.ssh.SshHostKeyVerification;
import com.github.stimur1709.cloudops.probe.ssh.SshProperties;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.PublicKey;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.connection.ConnectionException;
import net.schmizz.sshj.connection.channel.direct.Session;
import net.schmizz.sshj.transport.TransportException;
import net.schmizz.sshj.transport.verification.HostKeyVerifier;
import net.schmizz.sshj.transport.verification.OpenSSHKnownHosts;
import net.schmizz.sshj.transport.verification.PromiscuousVerifier;
import net.schmizz.sshj.userauth.UserAuthException;
import net.schmizz.sshj.userauth.keyprovider.KeyProvider;
import org.springframework.stereotype.Component;

@Component
public class SshClient {

    private final SshHostKeyVerification hostKeyVerification;
    private final Path knownHostsPath;

    public SshClient(SshProperties properties) {
        this.hostKeyVerification = properties.hostKeyVerification();
        this.knownHostsPath = properties.knownHostsPath();
    }

    public SshConnectionResult check(String host, int port, ResolvedCredential credential, int connectionTimeoutMs)
            throws SshClientException {
        try (SSHClient client = connect(host, port, credential, connectionTimeoutMs);
                Session ignored = client.startSession()) {
            return new SshConnectionResult(client.getTransport().getServerVersion());
        } catch (SshClientException exception) {
            throw exception;
        } catch (ConnectionException | TransportException exception) {
            throw failure(SshErrorType.CONNECTION, "SSH session could not be opened", exception);
        } catch (IOException exception) {
            throw mapConnectionFailure(exception);
        }
    }

    public SshCommandResult execute(
            String host, int port, ResolvedCredential credential, String command, Duration timeout, int maxOutputBytes)
            throws SshClientException {
        var executor = Executors.newVirtualThreadPerTaskExecutor();
        try (SSHClient client = connect(host, port, credential, Math.toIntExact(timeout.toMillis()));
                Session session = client.startSession();
                Session.Command remoteCommand = session.exec(command)) {
            OutputBudget budget = new OutputBudget(maxOutputBytes);
            Future<String> stdout = executor.submit(() -> read(remoteCommand.getInputStream(), budget));
            Future<String> stderr = executor.submit(() -> read(remoteCommand.getErrorStream(), budget));
            Future<?> completion = executor.submit((java.util.concurrent.Callable<Void>) () -> {
                remoteCommand.join();
                return null;
            });
            long startedAt = System.nanoTime();
            awaitCompletion(completion, timeout);
            long durationMs = Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
            return new SshCommandResult(
                    remoteCommand.getExitStatus() == null ? -1 : remoteCommand.getExitStatus(),
                    getOutput(stdout),
                    getOutput(stderr),
                    durationMs,
                    budget.truncated());
        } catch (SshClientException exception) {
            throw exception;
        } catch (ConnectionException | TransportException exception) {
            throw failure(SshErrorType.EXECUTION, "SSH command could not be executed", exception);
        } catch (IOException exception) {
            throw mapConnectionFailure(exception);
        } finally {
            executor.shutdownNow();
        }
    }

    private static void awaitCompletion(Future<?> completion, Duration timeout) throws SshClientException {
        try {
            completion.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException exception) {
            completion.cancel(true);
            throw failure(SshErrorType.COMMAND_TIMEOUT, "SSH command timed out", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw failure(SshErrorType.EXECUTION, "SSH command execution was interrupted", exception);
        } catch (ExecutionException exception) {
            throw failure(SshErrorType.EXECUTION, "SSH command could not be executed", exception);
        }
    }

    private SSHClient connect(String host, int port, ResolvedCredential credential, int timeoutMs)
            throws SshClientException {
        SSHClient client = new SSHClient();
        try {
            client.setConnectTimeout(timeoutMs);
            client.setTimeout(timeoutMs);
            AtomicBoolean hostKeyRejected = new AtomicBoolean();
            client.addHostKeyVerifier(hostKeyVerifier(hostKeyVerification, knownHostsPath, hostKeyRejected));
            KeyProvider keyProvider = loadKey(client, credential);
            try {
                client.connect(host, port);
            } catch (TransportException exception) {
                if (hostKeyRejected.get()) {
                    throw failure(SshErrorType.HOST_KEY, "SSH host key validation failed", exception);
                }
                throwIfTimedOut(exception);
                throw failure(SshErrorType.CONNECTION, "SSH connection could not be established", exception);
            }
            authenticate(client, credential, keyProvider);
            return client;
        } catch (SshClientException exception) {
            closeQuietly(client);
            throw exception;
        } catch (IOException exception) {
            closeQuietly(client);
            throw mapConnectionFailure(exception);
        }
    }

    private static String read(InputStream input, OutputBudget budget) throws IOException {
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) != -1) {
            int allowed = budget.reserve(count);
            captured.write(buffer, 0, allowed);
        }
        try {
            return StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.IGNORE)
                    .onUnmappableCharacter(CodingErrorAction.IGNORE)
                    .decode(ByteBuffer.wrap(captured.toByteArray()))
                    .toString();
        } catch (java.nio.charset.CharacterCodingException impossible) {
            throw new IllegalStateException("UTF-8 decoder was configured to ignore invalid input", impossible);
        }
    }

    private static String getOutput(Future<String> output) throws SshClientException {
        try {
            return output.get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw failure(SshErrorType.EXECUTION, "SSH command execution was interrupted", exception);
        } catch (ExecutionException exception) {
            throw failure(SshErrorType.EXECUTION, "SSH command output could not be read", exception);
        }
    }

    private static HostKeyVerifier hostKeyVerifier(
            SshHostKeyVerification verification, Path knownHostsPath, AtomicBoolean rejected)
            throws SshClientException {
        return switch (verification) {
            case ACCEPT_ALL -> new PromiscuousVerifier();
            case KNOWN_HOSTS -> strictVerifier(knownHostsPath, rejected);
        };
    }

    private static HostKeyVerifier strictVerifier(Path knownHostsPath, AtomicBoolean rejected)
            throws SshClientException {
        final OpenSSHKnownHosts knownHosts;
        try {
            knownHosts = new OpenSSHKnownHosts(knownHostsPath.toFile());
        } catch (IOException exception) {
            throw failure(SshErrorType.HOST_KEY, "SSH known_hosts could not be loaded", exception);
        }
        return new HostKeyVerifier() {
            @Override
            public boolean verify(String hostname, int port, PublicKey key) {
                boolean verified = knownHosts.verify(hostname, port, key);
                rejected.set(!verified);
                return verified;
            }

            @Override
            public List<String> findExistingAlgorithms(String hostname, int port) {
                return knownHosts.findExistingAlgorithms(hostname, port);
            }
        };
    }

    private static KeyProvider loadKey(SSHClient client, ResolvedCredential credential) throws SshClientException {
        if (!(credential instanceof ResolvedSshPrivateKey key)) {
            return null;
        }
        try {
            return client.loadKeys(key.privateKey(), null, null);
        } catch (IOException | RuntimeException exception) {
            throw failure(SshErrorType.CREDENTIAL, "SSH credential is invalid", exception);
        }
    }

    private static void authenticate(SSHClient client, ResolvedCredential credential, KeyProvider keyProvider)
            throws SshClientException {
        try {
            switch (credential) {
                case ResolvedUsernamePassword password -> client.authPassword(password.username(), password.password());
                case ResolvedSshPrivateKey key -> client.authPublickey(key.username(), keyProvider);
            }
        } catch (UserAuthException | TransportException exception) {
            throwIfTimedOut(exception);
            throw failure(SshErrorType.AUTHENTICATION, "SSH authentication failed", exception);
        }
    }

    private static void throwIfTimedOut(Exception exception) throws SshClientException {
        if (hasCause(exception, SocketTimeoutException.class)) {
            throw failure(SshErrorType.CONNECTION_TIMEOUT, "SSH connection timed out", exception);
        }
    }

    private static SshClientException mapConnectionFailure(IOException exception) {
        if (hasCause(exception, SocketTimeoutException.class)) {
            return failure(SshErrorType.CONNECTION_TIMEOUT, "SSH connection timed out", exception);
        }
        if (hasCause(exception, UnknownHostException.class)) {
            return failure(SshErrorType.CONNECTION, "SSH host could not be resolved", exception);
        }
        if (hasCause(exception, ConnectException.class)) {
            return failure(SshErrorType.CONNECTION, "SSH connection could not be established", exception);
        }
        return failure(SshErrorType.CONNECTION, "SSH connection could not be established", exception);
    }

    private static boolean hasCause(Throwable throwable, Class<? extends Throwable> type) {
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            if (type.isInstance(current)) {
                return true;
            }
        }
        return false;
    }

    private static SshClientException failure(SshErrorType type, String safeMessage, Throwable cause) {
        return new SshClientException(type, safeMessage, cause);
    }

    private static void closeQuietly(SSHClient client) {
        try {
            client.close();
        } catch (IOException ignored) {
            // Preserve the classified connection/authentication error.
        }
    }

    private static final class OutputBudget {
        private int remaining;
        private boolean truncated;

        private OutputBudget(int maximum) {
            this.remaining = maximum;
        }

        private synchronized int reserve(int requested) {
            int allowed = Math.min(remaining, requested);
            remaining -= allowed;
            truncated |= allowed < requested;
            return allowed;
        }

        private synchronized boolean truncated() {
            return truncated;
        }
    }
}
