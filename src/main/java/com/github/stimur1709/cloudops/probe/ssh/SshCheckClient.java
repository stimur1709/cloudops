package com.github.stimur1709.cloudops.probe.ssh;

import com.github.stimur1709.cloudops.credential.application.ResolvedCredential;
import com.github.stimur1709.cloudops.credential.application.ResolvedSshPrivateKey;
import com.github.stimur1709.cloudops.credential.application.ResolvedUsernamePassword;
import com.github.stimur1709.cloudops.probe.ProbeErrorCode;
import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.file.Path;
import java.security.PublicKey;
import java.time.Duration;
import java.util.List;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class SshCheckClient {

    private final SshHostKeyVerification hostKeyVerification;
    private final Path knownHostsPath;
    private final Connector connector;

    @Autowired
    public SshCheckClient(SshProperties properties) {
        this(properties.hostKeyVerification(), properties.knownHostsPath(), SshCheckClient::connect);
    }

    SshCheckClient(SshHostKeyVerification hostKeyVerification, Path knownHostsPath, Connector connector) {
        this.hostKeyVerification = hostKeyVerification;
        this.knownHostsPath = knownHostsPath;
        this.connector = connector;
    }

    SshCheckOutcome execute(String host, int port, ResolvedCredential credential, int timeoutMs) {
        long startedAt = System.nanoTime();
        try {
            ConnectionResult connection =
                    connector.connect(host, port, credential, timeoutMs, hostKeyVerification, knownHostsPath);
            long responseTimeMs =
                    Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
            return SshCheckOutcome.completed(new SshCheckResult(
                    host,
                    port,
                    credential.username(),
                    authMethod(credential),
                    connection.serverVersion(),
                    responseTimeMs));
        } catch (SshCheckException exception) {
            return SshCheckOutcome.failed(exception.errorCode(), exception.safeMessage());
        } catch (UnknownHostException exception) {
            return SshCheckOutcome.failed(ProbeErrorCode.DNS_ERROR, "Host name could not be resolved");
        } catch (SocketTimeoutException exception) {
            return SshCheckOutcome.failed(ProbeErrorCode.TIMEOUT, "SSH check timed out");
        } catch (ConnectException exception) {
            return SshCheckOutcome.failed(ProbeErrorCode.CONNECTION_ERROR, "SSH connection could not be established");
        } catch (IOException exception) {
            if (hasCause(exception, UnknownHostException.class)) {
                return SshCheckOutcome.failed(ProbeErrorCode.DNS_ERROR, "Host name could not be resolved");
            }
            if (hasCause(exception, SocketTimeoutException.class)) {
                return SshCheckOutcome.failed(ProbeErrorCode.TIMEOUT, "SSH check timed out");
            }
            return SshCheckOutcome.failed(ProbeErrorCode.CONNECTION_ERROR, "SSH connection could not be established");
        }
    }

    private static ConnectionResult connect(
            String host,
            int port,
            ResolvedCredential credential,
            int timeoutMs,
            SshHostKeyVerification hostKeyVerification,
            Path knownHostsPath)
            throws IOException {
        try (SSHClient client = new SSHClient()) {
            client.setConnectTimeout(timeoutMs);
            client.setTimeout(timeoutMs);
            AtomicBoolean hostKeyRejected = new AtomicBoolean();
            client.addHostKeyVerifier(hostKeyVerifier(hostKeyVerification, knownHostsPath, hostKeyRejected));
            KeyProvider keyProvider = loadKey(client, credential);
            try {
                client.connect(host, port);
            } catch (TransportException exception) {
                throwIfTimedOut(exception);
                if (hostKeyRejected.get()) {
                    throw failure(ProbeErrorCode.SSH_HOST_KEY_ERROR, "SSH host key validation failed", exception);
                }
                throw failure(ProbeErrorCode.SSH_HANDSHAKE_ERROR, "SSH handshake failed", exception);
            }
            authenticate(client, credential, keyProvider);
            try (Session ignored = client.startSession()) {
                return new ConnectionResult(client.getTransport().getServerVersion());
            } catch (ConnectionException | TransportException exception) {
                throwIfTimedOut(exception);
                throw failure(ProbeErrorCode.SSH_HANDSHAKE_ERROR, "SSH session could not be opened", exception);
            }
        }
    }

    private static HostKeyVerifier hostKeyVerifier(
            SshHostKeyVerification verification, Path knownHostsPath, AtomicBoolean rejected) throws SshCheckException {
        return switch (verification) {
            case ACCEPT_ALL -> new PromiscuousVerifier();
            case KNOWN_HOSTS -> strictVerifier(knownHostsPath, rejected);
        };
    }

    private static HostKeyVerifier strictVerifier(Path knownHostsPath, AtomicBoolean rejected)
            throws SshCheckException {
        final OpenSSHKnownHosts knownHosts;
        try {
            knownHosts = new OpenSSHKnownHosts(knownHostsPath.toFile());
        } catch (IOException exception) {
            throw failure(ProbeErrorCode.SSH_HOST_KEY_ERROR, "SSH known_hosts could not be loaded", exception);
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

    private static KeyProvider loadKey(SSHClient client, ResolvedCredential credential) throws SshCheckException {
        if (!(credential instanceof ResolvedSshPrivateKey key)) {
            return null;
        }
        try {
            return client.loadKeys(key.privateKey(), null, null);
        } catch (IOException | RuntimeException exception) {
            throw failure(ProbeErrorCode.CREDENTIAL_ERROR, "SSH credential is invalid", exception);
        }
    }

    private static void authenticate(SSHClient client, ResolvedCredential credential, KeyProvider keyProvider)
            throws SshCheckException {
        try {
            switch (credential) {
                case ResolvedUsernamePassword password -> client.authPassword(password.username(), password.password());
                case ResolvedSshPrivateKey key -> client.authPublickey(key.username(), keyProvider);
            }
        } catch (UserAuthException | TransportException exception) {
            throwIfTimedOut(exception);
            throw failure(ProbeErrorCode.SSH_AUTHENTICATION_ERROR, "SSH authentication failed", exception);
        }
    }

    private static SshAuthMethod authMethod(ResolvedCredential credential) {
        return switch (credential) {
            case ResolvedUsernamePassword ignored -> SshAuthMethod.PASSWORD;
            case ResolvedSshPrivateKey ignored -> SshAuthMethod.PUBLIC_KEY;
        };
    }

    private static SshCheckException failure(ProbeErrorCode code, String safeMessage, Exception cause) {
        return new SshCheckException(code, safeMessage, cause);
    }

    private static void throwIfTimedOut(Exception exception) throws SshCheckException {
        if (hasCause(exception, SocketTimeoutException.class)) {
            throw failure(ProbeErrorCode.TIMEOUT, "SSH check timed out", exception);
        }
    }

    private static boolean hasCause(Throwable throwable, Class<? extends Throwable> type) {
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            if (type.isInstance(current)) {
                return true;
            }
        }
        return false;
    }

    @FunctionalInterface
    interface Connector {
        ConnectionResult connect(
                String host,
                int port,
                ResolvedCredential credential,
                int timeoutMs,
                SshHostKeyVerification hostKeyVerification,
                Path knownHostsPath)
                throws IOException;
    }

    record ConnectionResult(String serverVersion) {}

    static final class SshCheckException extends IOException {
        private final ProbeErrorCode errorCode;
        private final String safeMessage;

        SshCheckException(ProbeErrorCode errorCode, String safeMessage, Throwable cause) {
            super(safeMessage, cause);
            this.errorCode = errorCode;
            this.safeMessage = safeMessage;
        }

        ProbeErrorCode errorCode() {
            return errorCode;
        }

        String safeMessage() {
            return safeMessage;
        }
    }
}
