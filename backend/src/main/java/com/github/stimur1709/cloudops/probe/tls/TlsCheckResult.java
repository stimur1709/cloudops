package com.github.stimur1709.cloudops.probe.tls;

import java.time.Instant;

public record TlsCheckResult(
        String host,
        int port,
        long responseTimeMs,
        String subject,
        String issuer,
        Instant notBefore,
        Instant notAfter,
        long daysUntilExpiry
) {
}
