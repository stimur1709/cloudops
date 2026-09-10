package com.github.stimur1709.cloudops.probe.ssh;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SshProperties.class)
public class SshConfiguration {}
