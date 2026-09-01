package com.github.stimur1709.cloudops.probe.ssh;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.github.stimur1709.cloudops.common.application.NotFoundException;
import com.github.stimur1709.cloudops.credential.CredentialPurpose;
import com.github.stimur1709.cloudops.credential.application.CredentialResolver;
import com.github.stimur1709.cloudops.credential.application.ResolvedUsernamePassword;
import com.github.stimur1709.cloudops.probe.ProbeErrorCode;
import com.github.stimur1709.cloudops.probe.ProbeType;
import com.github.stimur1709.cloudops.probe.execution.ProbeExecutionContext;
import com.github.stimur1709.cloudops.probe.execution.ProbeExecutionResult;
import com.github.stimur1709.cloudops.resource.config.DatabaseResourceConfig;
import com.github.stimur1709.cloudops.resource.config.NetworkDeviceResourceConfig;
import com.github.stimur1709.cloudops.resource.config.OtherResourceConfig;
import com.github.stimur1709.cloudops.resource.config.ServerResourceConfig;
import com.github.stimur1709.cloudops.resource.config.ServiceResourceConfig;
import org.junit.jupiter.api.Test;

class SshCheckHandlerTest {

    private final CredentialResolver credentialResolver = mock(CredentialResolver.class);
    private final SshCheckClient client = mock(SshCheckClient.class);
    private final SshCheckHandler handler = new SshCheckHandler(credentialResolver, client);

    @Test
    void supportsOnlyServerAndNetworkDeviceConfigs() {
        assertThat(handler.type()).isEqualTo(ProbeType.SSH_CHECK);
        assertThat(handler.isCompatibleWith(new ServerResourceConfig("server", 8080)))
                .isTrue();
        assertThat(handler.isCompatibleWith(new NetworkDeviceResourceConfig("switch", null)))
                .isTrue();
        assertThat(handler.isCompatibleWith(new ServiceResourceConfig("https://service", 200)))
                .isFalse();
        assertThat(handler.isCompatibleWith(new DatabaseResourceConfig("database", 5432, "cloudops")))
                .isFalse();
        assertThat(handler.isCompatibleWith(new OtherResourceConfig())).isFalse();
    }

    @Test
    void resolvesSshCredentialAndUsesCurrentTypeSpecificEndpointAndTimeout() {
        var credential = new ResolvedUsernamePassword("cloudops", "secret-password");
        var checkResult = new SshCheckResult("server", 2222, "cloudops", SshAuthMethod.PASSWORD, "SSH-2.0-test", 10);
        when(credentialResolver.resolve(7, CredentialPurpose.SSH)).thenReturn(credential);
        when(client.execute("server", 2222, credential, 987)).thenReturn(SshCheckOutcome.completed(checkResult));

        ProbeExecutionResult result = handler.execute(
                new ProbeExecutionContext(7, ProbeType.SSH_CHECK, new ServerResourceConfig("server", 8080, 2222), 987));

        assertThat(result).isEqualTo(ProbeExecutionResult.completed(true, checkResult));
        verify(credentialResolver).resolve(7, CredentialPurpose.SSH);
        verify(client).execute("server", 2222, credential, 987);
    }

    @Test
    void defaultsSshEndpointsToPort22() {
        var credential = new ResolvedUsernamePassword("cloudops", "secret-password");
        when(credentialResolver.resolve(8, CredentialPurpose.SSH)).thenReturn(credential);
        when(client.execute("switch", 22, credential, 5000))
                .thenReturn(SshCheckOutcome.failed(ProbeErrorCode.CONNECTION_ERROR, "connection failed"));

        ProbeExecutionResult result = handler.execute(
                new ProbeExecutionContext(8, ProbeType.SSH_CHECK, new NetworkDeviceResourceConfig("switch", null)));

        assertThat(result).isEqualTo(ProbeExecutionResult.failed(ProbeErrorCode.CONNECTION_ERROR, "connection failed"));
        verify(client).execute("switch", 22, credential, 5000);
    }

    @Test
    void reportsMissingCredentialWithoutCallingClient() {
        when(credentialResolver.resolve(9, CredentialPurpose.SSH)).thenThrow(new NotFoundException());

        ProbeExecutionResult result = handler.execute(
                new ProbeExecutionContext(9, ProbeType.SSH_CHECK, new ServerResourceConfig("server", null)));

        assertThat(result)
                .isEqualTo(ProbeExecutionResult.failed(
                        ProbeErrorCode.CREDENTIAL_NOT_CONFIGURED, "SSH credential is not configured"));
    }

    @Test
    void newlyBoundCredentialIsUsedByTheNextExecution() {
        var credential = new ResolvedUsernamePassword("cloudops", "secret-password");
        var checkResult = new SshCheckResult("server", 22, "cloudops", SshAuthMethod.PASSWORD, "test-server", 4);
        when(credentialResolver.resolve(10, CredentialPurpose.SSH))
                .thenThrow(new NotFoundException())
                .thenReturn(credential);
        when(client.execute("server", 22, credential, 5000)).thenReturn(SshCheckOutcome.completed(checkResult));
        var context = new ProbeExecutionContext(10, ProbeType.SSH_CHECK, new ServerResourceConfig("server", null));

        assertThat(handler.execute(context))
                .isEqualTo(ProbeExecutionResult.failed(
                        ProbeErrorCode.CREDENTIAL_NOT_CONFIGURED, "SSH credential is not configured"));
        assertThat(handler.execute(context)).isEqualTo(ProbeExecutionResult.completed(true, checkResult));
    }

    @Test
    void rejectsUnsupportedConfigAtExecutionBoundary() {
        assertThatThrownBy(() ->
                        handler.execute(new ProbeExecutionContext(1, ProbeType.SSH_CHECK, new OtherResourceConfig())))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
