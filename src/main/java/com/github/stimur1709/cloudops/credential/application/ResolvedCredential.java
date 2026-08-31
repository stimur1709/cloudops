package com.github.stimur1709.cloudops.credential.application;

public sealed interface ResolvedCredential permits ResolvedUsernamePassword, ResolvedSshPrivateKey {
    String username();
}
