package com.github.stimur1709.cloudops.task.application;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.support.RetryTemplate;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(TaskRetryProperties.class)
public class TaskRetryConfiguration {

    @Bean
    RetryTemplate taskRetryTemplate(TaskRetryProperties properties) {
        int maxAttempts = properties.enabled() ? properties.maxAttempts() : 1;
        return RetryTemplate.builder()
                .maxAttempts(maxAttempts)
                .retryOn(RetryableTaskExecutionException.class)
                .exponentialBackoff(
                        properties.initialInterval(), properties.multiplier(), properties.maxInterval()
                )
                .build();
    }
}
