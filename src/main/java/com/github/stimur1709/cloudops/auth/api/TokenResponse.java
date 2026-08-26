package com.github.stimur1709.cloudops.auth.api;

public record TokenResponse(String accessToken, String tokenType, long expiresIn) {
}
