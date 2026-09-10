package com.github.stimur1709.cloudops.credential.application;

public record ResolvedUsernamePassword(String username, String password) implements ResolvedCredential {}
