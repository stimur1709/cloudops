package com.github.stimur1709.cloudops.task.messaging;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("cloudops.task.messaging")
public record TaskMessagingProperties(
        @NotBlank String exchange,
        @NotBlank String queue,
        @NotBlank String routingKey,
        @NotBlank String deadLetterExchange,
        @NotBlank String deadLetterQueue,
        @NotBlank String deadLetterRoutingKey
) {
}
