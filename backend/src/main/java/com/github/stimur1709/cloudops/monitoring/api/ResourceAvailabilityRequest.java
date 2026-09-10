package com.github.stimur1709.cloudops.monitoring.api;

import com.github.stimur1709.cloudops.monitoring.api.validation.ValidAvailabilityPeriod;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

@ValidAvailabilityPeriod
public record ResourceAvailabilityRequest(
        @NotNull(message = "From is required") Instant from,
        @NotNull(message = "To is required") Instant to) {}
