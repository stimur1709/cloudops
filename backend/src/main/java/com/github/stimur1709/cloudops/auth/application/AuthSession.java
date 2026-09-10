package com.github.stimur1709.cloudops.auth.application;

import com.github.stimur1709.cloudops.auth.api.TokenResponse;

public record AuthSession(TokenResponse response, String refreshToken) {}
