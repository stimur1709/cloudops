package com.github.stimur1709.cloudops.task.messaging;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("cloudops.task.messaging")
public record TaskMessagingProperties(String exchange, String queue, String routingKey) {
}
