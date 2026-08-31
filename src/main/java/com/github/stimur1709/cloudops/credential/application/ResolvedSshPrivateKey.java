package com.github.stimur1709.cloudops.credential.application;

public record ResolvedSshPrivateKey(String username, String privateKey) implements ResolvedCredential {
}
