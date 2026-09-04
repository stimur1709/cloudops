package com.github.stimur1709.cloudops.ssh;

import java.io.IOException;

public final class SshClientException extends IOException {

    private final SshErrorType type;
    private final String safeMessage;

    public SshClientException(SshErrorType type, String safeMessage, Throwable cause) {
        super(safeMessage, cause);
        this.type = type;
        this.safeMessage = safeMessage;
    }

    public SshErrorType type() {
        return type;
    }

    public String safeMessage() {
        return safeMessage;
    }

    public boolean retriable() {
        return type.retriable();
    }
}
