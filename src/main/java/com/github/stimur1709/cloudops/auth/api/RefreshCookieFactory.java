package com.github.stimur1709.cloudops.auth.api;

import com.github.stimur1709.cloudops.auth.config.RefreshTokenProperties;
import java.time.Duration;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
public class RefreshCookieFactory {

    private final RefreshTokenProperties properties;

    public RefreshCookieFactory(RefreshTokenProperties properties) {
        this.properties = properties;
    }

    public String name() {
        return properties.cookie().name();
    }

    public ResponseCookie create(String token, Duration maxAge) {
        return cookie(token).maxAge(maxAge).build();
    }

    public ResponseCookie clear() {
        return cookie("").maxAge(Duration.ZERO).build();
    }

    private ResponseCookie.ResponseCookieBuilder cookie(String value) {
        var cookie = properties.cookie();
        return ResponseCookie.from(cookie.name(), value)
                .httpOnly(true)
                .secure(cookie.secure())
                .sameSite(cookie.sameSite().value())
                .path(cookie.path());
    }
}
