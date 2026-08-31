package com.github.stimur1709.cloudops.credential.crypto;

import java.util.Base64;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("cloudops.security.credentials")
public record CredentialCryptoProperties(String masterKey) {
    public CredentialCryptoProperties {
        if (masterKey == null || masterKey.isBlank()) {
            throw new IllegalArgumentException("Credential master key is required");
        }
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(masterKey);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Credential master key must be Base64 encoded", exception);
        }
        if (decoded.length != 32) {
            throw new IllegalArgumentException("Credential master key must contain exactly 256 bits");
        }
    }

    SecretKey key() {
        return new SecretKeySpec(Base64.getDecoder().decode(masterKey), "AES");
    }
}
