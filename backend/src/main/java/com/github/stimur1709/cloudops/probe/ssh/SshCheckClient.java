package com.github.stimur1709.cloudops.probe.ssh;

import com.github.stimur1709.cloudops.credential.application.ResolvedCredential;
import com.github.stimur1709.cloudops.probe.ProbeErrorCode;
import com.github.stimur1709.cloudops.ssh.SshClient;
import com.github.stimur1709.cloudops.ssh.SshClientException;
import java.time.Duration;
import org.springframework.stereotype.Component;

@Component
public class SshCheckClient {

    private final SshClient client;

    public SshCheckClient(SshClient client) {
        this.client = client;
    }

    SshCheckOutcome execute(String host, int port, ResolvedCredential credential, int timeoutMs) {
        long startedAt = System.nanoTime();
        try {
            var connection = client.check(host, port, credential, timeoutMs);
            long responseTimeMs =
                    Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
            return SshCheckOutcome.completed(new SshCheckResult(
                    host,
                    port,
                    credential.username(),
                    SshAuthMethod.from(credential),
                    connection.serverVersion(),
                    responseTimeMs));
        } catch (SshClientException exception) {
            return SshCheckOutcome.failed(errorCode(exception), exception.safeMessage());
        }
    }

    private ProbeErrorCode errorCode(SshClientException exception) {
        return switch (exception.type()) {
            case CONNECTION -> ProbeErrorCode.CONNECTION_ERROR;
            case CONNECTION_TIMEOUT, COMMAND_TIMEOUT -> ProbeErrorCode.TIMEOUT;
            case HOST_KEY -> ProbeErrorCode.SSH_HOST_KEY_ERROR;
            case AUTHENTICATION -> ProbeErrorCode.SSH_AUTHENTICATION_ERROR;
            case CREDENTIAL -> ProbeErrorCode.CREDENTIAL_ERROR;
            case EXECUTION -> ProbeErrorCode.SSH_HANDSHAKE_ERROR;
        };
    }
}
