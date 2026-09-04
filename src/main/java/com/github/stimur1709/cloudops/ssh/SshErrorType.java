package com.github.stimur1709.cloudops.ssh;

public enum SshErrorType {
    CONNECTION(true),
    CONNECTION_TIMEOUT(true),
    HOST_KEY(false),
    AUTHENTICATION(false),
    CREDENTIAL(false),
    EXECUTION(false),
    COMMAND_TIMEOUT(false);

    private final boolean retriable;

    SshErrorType(boolean retriable) {
        this.retriable = retriable;
    }

    public boolean retriable() {
        return retriable;
    }
}
