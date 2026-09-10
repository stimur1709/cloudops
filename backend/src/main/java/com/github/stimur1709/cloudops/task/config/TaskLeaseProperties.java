package com.github.stimur1709.cloudops.task.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties("cloudops.task.lease")
@Validated
public record TaskLeaseProperties(
        boolean enabled,
        @NotNull Duration duration,
        @NotNull Duration heartbeatInterval,
        @NotNull Duration recoveryPollInterval,
        @Positive int recoveryBatchSize,
        @PositiveOrZero int maxRecoveries) {
    @AssertTrue(message = "heartbeat interval must be less than lease duration") public boolean isHeartbeatShorterThanLease() {
        return duration != null
                && heartbeatInterval != null
                && duration.isPositive()
                && heartbeatInterval.isPositive()
                && heartbeatInterval.compareTo(duration) < 0;
    }

    @AssertTrue(message = "recovery poll interval must be positive") public boolean isRecoveryPollIntervalPositive() {
        return recoveryPollInterval != null && recoveryPollInterval.isPositive();
    }
}
