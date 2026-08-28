package com.github.stimur1709.cloudops.probe.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({PortCheckProperties.class, PingProperties.class, TlsCheckProperties.class})
public class ProbeConfiguration {
}
