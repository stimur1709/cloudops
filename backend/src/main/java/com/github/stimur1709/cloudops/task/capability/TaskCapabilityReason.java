package com.github.stimur1709.cloudops.task.capability;

import com.github.stimur1709.cloudops.common.application.ConflictException;

public enum TaskCapabilityReason {
    UNSUPPORTED_RESOURCE_TYPE("RESOURCE_UNSUPPORTED", "Task is not supported by the resource type"),
    RESOURCE_INACTIVE("RESOURCE_INACTIVE", "Task requires an active resource"),
    SSH_ENDPOINT_NOT_CONFIGURED("SSH_ENDPOINT_NOT_CONFIGURED", "SSH endpoint is not configured for the resource"),
    SSH_CREDENTIAL_NOT_CONFIGURED("SSH_CREDENTIAL_NOT_CONFIGURED", "SSH credential is not configured for the resource"),
    NOT_AUTHORIZED(null, null);

    private final String conflictCode;
    private final String conflictMessage;

    TaskCapabilityReason(String conflictCode, String conflictMessage) {
        this.conflictCode = conflictCode;
        this.conflictMessage = conflictMessage;
    }

    ConflictException conflict() {
        if (conflictCode == null) {
            throw new IllegalStateException(this + " is not an availability conflict");
        }
        return new ConflictException(conflictCode, conflictMessage);
    }
}
