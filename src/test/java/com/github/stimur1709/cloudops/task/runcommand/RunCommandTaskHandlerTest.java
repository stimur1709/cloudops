package com.github.stimur1709.cloudops.task.runcommand;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.github.stimur1709.cloudops.credential.CredentialPurpose;
import com.github.stimur1709.cloudops.credential.application.CredentialResolver;
import com.github.stimur1709.cloudops.credential.application.ResolvedUsernamePassword;
import com.github.stimur1709.cloudops.resource.ResourceStatus;
import com.github.stimur1709.cloudops.resource.config.ServerResourceConfig;
import com.github.stimur1709.cloudops.ssh.SshClient;
import com.github.stimur1709.cloudops.ssh.SshClientException;
import com.github.stimur1709.cloudops.ssh.SshCommandResult;
import com.github.stimur1709.cloudops.ssh.SshErrorType;
import com.github.stimur1709.cloudops.task.TaskErrorCode;
import com.github.stimur1709.cloudops.task.TaskType;
import com.github.stimur1709.cloudops.task.execution.RetryableTaskExecutionException;
import com.github.stimur1709.cloudops.task.execution.TaskExecutionContext;
import com.github.stimur1709.cloudops.task.execution.TaskExecutionResult;
import com.github.stimur1709.cloudops.task.parameters.TaskParameterCodecRegistry;
import jakarta.validation.Validation;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class RunCommandTaskHandlerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final CredentialResolver credentialResolver = mock(CredentialResolver.class);
    private final SshClient sshClient = mock(SshClient.class);
    private RunCommandTaskHandler handler;

    @BeforeEach
    void setUp() {
        var codec = new TaskParameterCodecRegistry(
                objectMapper,
                Validation.buildDefaultValidatorFactory().getValidator(),
                List.of(new RunCommandTaskDefinition()));
        handler = new RunCommandTaskHandler(
                codec, credentialResolver, sshClient, new RunCommandProperties(Duration.ofSeconds(2), 1024));
    }

    @Test
    void returnsTypedResultAndTreatsNonZeroExitAsCompleted() throws Exception {
        when(credentialResolver.resolve(9, CredentialPurpose.SSH))
                .thenReturn(new ResolvedUsernamePassword("cloudops", "top-secret"));
        when(sshClient.execute(anyString(), anyInt(), any(), anyString(), any(), anyInt()))
                .thenReturn(new SshCommandResult(12, "out", "err", 25, false));

        TaskExecutionResult result = handler.execute(context());

        assertThat(result).isInstanceOfSatisfying(TaskExecutionResult.Completed.class, completed -> {
            assertThat(completed.data()).isEqualTo(new RunCommandResult(12, "out", "err", 25, false));
            assertThat(completed.toString()).doesNotContain("top-secret");
        });
    }

    @Test
    void classifiesTemporaryConnectionFailureAsRetriable() throws Exception {
        when(credentialResolver.resolve(9, CredentialPurpose.SSH))
                .thenReturn(new ResolvedUsernamePassword("cloudops", "top-secret"));
        when(sshClient.execute(anyString(), anyInt(), any(), anyString(), any(), anyInt()))
                .thenThrow(new SshClientException(
                        SshErrorType.CONNECTION, "SSH connection could not be established", null));

        assertThatThrownBy(() -> handler.execute(context()))
                .isInstanceOfSatisfying(
                        RetryableTaskExecutionException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(TaskErrorCode.SSH_CONNECTION_ERROR));
    }

    @Test
    void authenticationAndTimeoutAreControlledNonRetriableFailures() throws Exception {
        when(credentialResolver.resolve(9, CredentialPurpose.SSH))
                .thenReturn(new ResolvedUsernamePassword("cloudops", "top-secret"));
        when(sshClient.execute(anyString(), anyInt(), any(), anyString(), any(), anyInt()))
                .thenThrow(new SshClientException(SshErrorType.AUTHENTICATION, "SSH authentication failed", null))
                .thenThrow(new SshClientException(SshErrorType.COMMAND_TIMEOUT, "SSH command timed out", null));

        assertThat(handler.execute(context()))
                .isEqualTo(TaskExecutionResult.failed(
                        TaskErrorCode.SSH_AUTHENTICATION_ERROR, "SSH authentication failed"));
        assertThat(handler.execute(context()))
                .isEqualTo(TaskExecutionResult.failed(TaskErrorCode.COMMAND_TIMEOUT, "SSH command timed out"));
    }

    private TaskExecutionContext context() {
        return new TaskExecutionContext(
                9,
                TaskType.RUN_COMMAND,
                objectMapper.createObjectNode().put("command", "false"),
                ResourceStatus.ACTIVE,
                new ServerResourceConfig("host", null));
    }
}
