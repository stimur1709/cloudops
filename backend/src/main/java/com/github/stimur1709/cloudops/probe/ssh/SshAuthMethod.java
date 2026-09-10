package com.github.stimur1709.cloudops.probe.ssh;

import com.github.stimur1709.cloudops.credential.application.ResolvedCredential;
import com.github.stimur1709.cloudops.credential.application.ResolvedSshPrivateKey;
import com.github.stimur1709.cloudops.credential.application.ResolvedUsernamePassword;

public enum SshAuthMethod {
    PASSWORD,
    PUBLIC_KEY;

    static SshAuthMethod from(ResolvedCredential credential) {
        return switch (credential) {
            case ResolvedUsernamePassword ignored -> PASSWORD;
            case ResolvedSshPrivateKey ignored -> PUBLIC_KEY;
        };
    }
}
