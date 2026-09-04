package com.github.stimur1709.cloudops.task.runcommand;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(RunCommandProperties.class)
class RunCommandConfiguration {}
