package com.github.stimur1709.cloudops.monitoring.application;

import java.math.BigDecimal;
import java.time.Instant;

public record ResourceAvailability(
        Instant from,
        Instant to,
        long periodSeconds,
        long upSeconds,
        long degradedSeconds,
        long downSeconds,
        long unknownSeconds,
        long knownSeconds,
        BigDecimal uptimePercent,
        BigDecimal availabilityPercent,
        BigDecimal coveragePercent
) {
}
