package com.github.stimur1709.cloudops.auth.application;

public enum RefreshTokenError {
    INVALID("REFRESH_TOKEN_INVALID"),
    EXPIRED("REFRESH_TOKEN_EXPIRED"),
    REVOKED("REFRESH_TOKEN_REVOKED");

    private final String code;

    RefreshTokenError(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
