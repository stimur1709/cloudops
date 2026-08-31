package com.github.stimur1709.cloudops.probe.dns;

import java.util.List;

public record DnsCheckResult(String hostname, List<String> addresses, long responseTimeMs) {}
