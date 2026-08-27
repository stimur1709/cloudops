package com.github.stimur1709.cloudops.task.application;

import java.time.Duration;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("cloudops.task.retry")
public record TaskRetryProperties(
        boolean enabled,
        @Min(1) int maxAttempts,
        @NotNull Duration initialInterval,
        @DecimalMin("1.0") double multiplier,
        @NotNull Duration maxInterval
) {
    @AssertTrue(message = "retry intervals must be positive and max interval must not be less than initial interval")
    public boolean isBackoffValid() {
        return initialInterval != null
                && maxInterval != null
                && initialInterval.isPositive()
                && maxInterval.isPositive()
                && maxInterval.compareTo(initialInterval) >= 0;
    }

    Duration intervalAfterFailure(int failureNumber) {
        double factor = Math.pow(multiplier, Math.max(0, failureNumber - 1));
        long millis = Math.min(maxInterval.toMillis(), (long) (initialInterval.toMillis() * factor));
        return Duration.ofMillis(millis);
    }
}
