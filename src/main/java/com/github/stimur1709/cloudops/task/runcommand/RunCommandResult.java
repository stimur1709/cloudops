package com.github.stimur1709.cloudops.task.runcommand;

public record RunCommandResult(
        Integer exitCode, String stdout, String stderr, long durationMs, boolean outputTruncated) {}
