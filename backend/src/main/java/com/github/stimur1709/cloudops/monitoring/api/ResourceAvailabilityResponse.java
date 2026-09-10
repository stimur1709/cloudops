package com.github.stimur1709.cloudops.monitoring.api;

import com.github.stimur1709.cloudops.monitoring.application.ResourceAvailability;
import java.math.BigDecimal;
import java.time.Instant;

public record ResourceAvailabilityResponse(
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
        BigDecimal coveragePercent) {

    public static ResourceAvailabilityResponse from(ResourceAvailability availability) {
        return new ResourceAvailabilityResponse(
                availability.from(),
                availability.to(),
                availability.periodSeconds(),
                availability.upSeconds(),
                availability.degradedSeconds(),
                availability.downSeconds(),
                availability.unknownSeconds(),
                availability.knownSeconds(),
                availability.uptimePercent(),
                availability.availabilityPercent(),
                availability.coveragePercent());
    }
}
