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
import com.github.stimur1709.cloudops.resource.config.ResourceConfig;
import com.github.stimur1709.cloudops.ssh.SshEndpointResolver;
import org.springframework.stereotype.Component;

@Component
public class SshCheckHandler implements ProbeHandler {

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
        return SshEndpointResolver.supports(resourceConfig);
    }

    @Override
    public ProbeExecutionResult execute(ProbeExecutionContext context) {
        var endpoint = SshEndpointResolver.resolve(context.resourceConfig());
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
}
