package com.github.stimur1709.cloudops.credential.config;

import com.github.stimur1709.cloudops.credential.crypto.CredentialCryptoProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(CredentialCryptoProperties.class)
public class CredentialConfiguration {
}
