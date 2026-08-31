package com.github.stimur1709.cloudops.common.config;

import java.time.Duration;
import java.util.Base64;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("cloudops.security.jwt")
public record JwtProperties(String secret, String issuer, Duration accessTokenTtl) {

    public JwtProperties {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("JWT secret is required");
        }
        if (issuer == null || issuer.isBlank()) {
            throw new IllegalArgumentException("JWT issuer is required");
        }
        if (accessTokenTtl == null || accessTokenTtl.isZero() || accessTokenTtl.isNegative()) {
            throw new IllegalArgumentException("JWT access token TTL must be positive");
        }
        byte[] key;
        try {
            key = Base64.getDecoder().decode(secret);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("JWT secret must be Base64 encoded", exception);
        }
        if (key.length < 32) {
            throw new IllegalArgumentException("JWT secret must contain at least 256 bits");
        }
    }

    public SecretKey key() {
        return new SecretKeySpec(Base64.getDecoder().decode(secret), "HmacSHA256");
    }
}
