package com.github.stimur1709.cloudops.monitoring.api;

import java.time.Instant;

import com.github.stimur1709.cloudops.monitoring.api.validation.ValidAvailabilityPeriod;
import jakarta.validation.constraints.NotNull;

@ValidAvailabilityPeriod
public record ResourceAvailabilityRequest(
        @NotNull(message = "From is required") Instant from,
        @NotNull(message = "To is required") Instant to
) {
}
