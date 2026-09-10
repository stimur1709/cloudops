package com.github.stimur1709.cloudops.auth.api;

import java.time.Instant;

public record TokenResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        Instant accessTokenExpiresAt,
        Instant refreshTokenExpiresAt) {}
