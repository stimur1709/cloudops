package com.github.stimur1709.cloudops.probe.ssh;

import com.github.stimur1709.cloudops.common.application.NotFoundException;
import com.github.stimur1709.cloudops.credential.CredentialPurpose;
import com.github.stimur1709.cloudops.credential.application.CredentialResolver;
import com.github.stimur1709.cloudops.credential.application.ResolvedCredential;
import com.github.stimur1709.cloudops.credential.crypto.SecretDecryptionException;
import com.github.stimur1709.cloudops.probe.ProbeErrorCode;
import com.github.stimur1709.cloudops.probe.ProbeType;
import com.github.stimur1709.cloudops.probe.execution.ProbeExecutionContext;
import com.github.stimur1709.cloudops.probe.execution.ProbeExecutionResult;
import com.github.stimur1709.cloudops.probe.execution.ProbeHandler;
import com.github.stimur1709.cloudops.resource.config.NetworkDeviceResourceConfig;
import com.github.stimur1709.cloudops.resource.config.ResourceConfig;
import com.github.stimur1709.cloudops.resource.config.ServerResourceConfig;
import org.springframework.stereotype.Component;

@Component
public class SshCheckHandler implements ProbeHandler {

    private static final int DEFAULT_SSH_PORT = 22;

    private final CredentialResolver credentialResolver;
    private final SshCheckClient client;

    public SshCheckHandler(CredentialResolver credentialResolver, SshCheckClient client) {
        this.credentialResolver = credentialResolver;
        this.client = client;
    }

    @Override
    public ProbeType type() {
        return ProbeType.SSH_CHECK;
    }

    @Override
    public boolean isCompatibleWith(ResourceConfig resourceConfig) {
        return resourceConfig instanceof ServerResourceConfig || resourceConfig instanceof NetworkDeviceResourceConfig;
    }

    @Override
    public ProbeExecutionResult execute(ProbeExecutionContext context) {
        Endpoint endpoint = endpoint(context.resourceConfig());
        final ResolvedCredential credential;
        try {
            credential = credentialResolver.resolve(context.resourceId(), CredentialPurpose.SSH);
        } catch (NotFoundException exception) {
            return ProbeExecutionResult.failed(
                    ProbeErrorCode.CREDENTIAL_NOT_CONFIGURED, "SSH credential is not configured");
        } catch (SecretDecryptionException exception) {
            return ProbeExecutionResult.failed(ProbeErrorCode.CREDENTIAL_ERROR, "SSH credential could not be read");
        }
        SshCheckOutcome outcome = client.execute(endpoint.host(), endpoint.port(), credential, context.timeoutMs());
        if (outcome.completed()) {
            return ProbeExecutionResult.completed(true, outcome.result());
        }
        return ProbeExecutionResult.failed(outcome.errorCode(), outcome.errorMessage());
    }

    private Endpoint endpoint(ResourceConfig resourceConfig) {
        return switch (resourceConfig) {
            case ServerResourceConfig server -> new Endpoint(server.host(), server.sshPort());
            case NetworkDeviceResourceConfig device ->
                new Endpoint(
                        device.host(), device.managementPort() == null ? DEFAULT_SSH_PORT : device.managementPort());
            default -> throw new IllegalArgumentException("SSH_CHECK requires a server or network device");
        };
    }

    private record Endpoint(String host, int port) {}
}
