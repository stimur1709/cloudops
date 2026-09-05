package com.github.stimur1709.cloudops.task.runcommand;

import com.github.stimur1709.cloudops.common.application.NotFoundException;
import com.github.stimur1709.cloudops.credential.CredentialPurpose;
import com.github.stimur1709.cloudops.credential.application.CredentialResolver;
import com.github.stimur1709.cloudops.credential.application.ResolvedCredential;
import com.github.stimur1709.cloudops.credential.crypto.SecretDecryptionException;
import com.github.stimur1709.cloudops.resource.ResourceStatus;
import com.github.stimur1709.cloudops.ssh.SshClient;
import com.github.stimur1709.cloudops.ssh.SshClientException;
import com.github.stimur1709.cloudops.ssh.SshEndpointResolver;
import com.github.stimur1709.cloudops.task.TaskErrorCode;
import com.github.stimur1709.cloudops.task.TaskType;
import com.github.stimur1709.cloudops.task.execution.RetryableTaskExecutionException;
import com.github.stimur1709.cloudops.task.execution.TaskExecutionContext;
import com.github.stimur1709.cloudops.task.execution.TaskExecutionResult;
import com.github.stimur1709.cloudops.task.execution.TaskHandler;
import com.github.stimur1709.cloudops.task.parameters.TaskParameterCodecRegistry;
import org.springframework.stereotype.Component;

@Component
public class RunCommandTaskHandler implements TaskHandler {

    private final TaskParameterCodecRegistry parameterCodecRegistry;
    private final CredentialResolver credentialResolver;
    private final SshClient sshClient;
    private final RunCommandProperties properties;

    public RunCommandTaskHandler(
            TaskParameterCodecRegistry parameterCodecRegistry,
            CredentialResolver credentialResolver,
            SshClient sshClient,
            RunCommandProperties properties) {
        this.parameterCodecRegistry = parameterCodecRegistry;
        this.credentialResolver = credentialResolver;
        this.sshClient = sshClient;
        this.properties = properties;
    }

    @Override
    public TaskType type() {
        return TaskType.RUN_COMMAND;
    }

    @Override
    public TaskExecutionResult execute(TaskExecutionContext context) {
        if (context.resourceStatus() != ResourceStatus.ACTIVE) {
            return TaskExecutionResult.failed(TaskErrorCode.RESOURCE_INACTIVE, "Resource is no longer active");
        }
        if (!SshEndpointResolver.supports(context.resourceConfig())) {
            return TaskExecutionResult.failed(
                    TaskErrorCode.RESOURCE_UNSUPPORTED, "Resource no longer supports RUN_COMMAND");
        }
        RunCommandParameters parameters =
                parameterCodecRegistry.decode(context.type(), context.parameters(), RunCommandParameters.class);
        final ResolvedCredential credential;
        try {
            credential = credentialResolver.resolve(context.resourceId(), CredentialPurpose.SSH);
        } catch (NotFoundException exception) {
            return TaskExecutionResult.failed(
                    TaskErrorCode.SSH_CREDENTIAL_NOT_CONFIGURED, "SSH credential is not configured");
        } catch (SecretDecryptionException exception) {
            return TaskExecutionResult.failed(TaskErrorCode.SSH_CREDENTIAL_ERROR, "SSH credential could not be read");
        }

        var endpoint = SshEndpointResolver.resolve(context.resourceConfig());
        try {
            var result = sshClient.execute(
                    endpoint.host(),
                    endpoint.port(),
                    credential,
                    parameters.command(),
                    properties.timeout(),
                    properties.maxOutputBytes());
            return TaskExecutionResult.completed(new RunCommandResult(
                    result.exitCode(),
                    result.stdout(),
                    result.stderr(),
                    result.durationMs(),
                    result.outputTruncated()));
        } catch (SshClientException exception) {
            TaskErrorCode errorCode = errorCode(exception);
            if (exception.retriable()) {
                throw new RetryableTaskExecutionException(errorCode, exception.safeMessage(), exception);
            }
            return TaskExecutionResult.failed(errorCode, exception.safeMessage());
        }
    }

    private TaskErrorCode errorCode(SshClientException exception) {
        return switch (exception.type()) {
            case CONNECTION, CONNECTION_TIMEOUT -> TaskErrorCode.SSH_CONNECTION_ERROR;
            case HOST_KEY -> TaskErrorCode.SSH_HOST_KEY_ERROR;
            case AUTHENTICATION -> TaskErrorCode.SSH_AUTHENTICATION_ERROR;
            case CREDENTIAL -> TaskErrorCode.SSH_CREDENTIAL_ERROR;
            case EXECUTION -> TaskErrorCode.SSH_EXECUTION_ERROR;
            case COMMAND_TIMEOUT -> TaskErrorCode.COMMAND_TIMEOUT;
        };
    }
}
