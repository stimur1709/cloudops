package com.github.stimur1709.cloudops.task.runcommand;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("cloudops.task.run-command")
public record RunCommandProperties(
        @NotNull Duration timeout,

        @Min(value = 1, message = "Maximum command output must be positive") int maxOutputBytes) {

    @AssertTrue(message = "Command timeout must be positive") public boolean isTimeoutPositive() {
        return timeout != null && !timeout.isZero() && !timeout.isNegative();
    }
}
