package com.github.stimur1709.cloudops.ssh;

public record SshCommandResult(int exitCode, String stdout, String stderr, long durationMs, boolean outputTruncated) {}
