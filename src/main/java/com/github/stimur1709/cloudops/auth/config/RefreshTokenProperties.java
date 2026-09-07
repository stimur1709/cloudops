package com.github.stimur1709.cloudops.auth.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("cloudops.security.refresh")
public record RefreshTokenProperties(
        Cookie cookie,
        boolean cleanupEnabled,
        Duration cleanupInterval,
        int cleanupBatchSize,
        Duration revokedRetention) {

    public RefreshTokenProperties {
        if (cookie == null) {
            throw new IllegalArgumentException("Refresh cookie settings are required");
        }
        if (cleanupInterval == null || cleanupInterval.isZero() || cleanupInterval.isNegative()) {
            throw new IllegalArgumentException("Refresh cleanup interval must be positive");
        }
        if (cleanupBatchSize < 1) {
            throw new IllegalArgumentException("Refresh cleanup batch size must be positive");
        }
        if (revokedRetention == null || revokedRetention.isNegative()) {
            throw new IllegalArgumentException("Refresh revoked retention must not be negative");
        }
    }

    public record Cookie(String name, String path, boolean secure, SameSite sameSite) {

        public Cookie {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("Refresh cookie name is required");
            }
            if (path == null || path.isBlank() || !path.startsWith("/")) {
                throw new IllegalArgumentException("Refresh cookie path must be absolute");
            }
            if (sameSite == null) {
                throw new IllegalArgumentException("Refresh cookie SameSite is required");
            }
        }
    }

    public enum SameSite {
        STRICT("Strict"),
        LAX("Lax");

        private final String value;

        SameSite(String value) {
            this.value = value;
        }

        public String value() {
            return value;
        }
    }
}
