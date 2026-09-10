package com.github.stimur1709.cloudops.task.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(TaskLeaseProperties.class)
class TaskLeaseConfiguration {}
