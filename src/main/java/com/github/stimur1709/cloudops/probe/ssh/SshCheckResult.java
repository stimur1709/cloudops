package com.github.stimur1709.cloudops.probe.ssh;

public record SshCheckResult(
        String host, int port, String username, SshAuthMethod authMethod, String serverVersion, long responseTimeMs) {}
