package com.github.stimur1709.cloudops.probe.port;

public record PortCheckResult(String host, int port, long responseTimeMs) {}
