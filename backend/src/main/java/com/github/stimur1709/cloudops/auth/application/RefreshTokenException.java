package com.github.stimur1709.cloudops.auth.application;

public class RefreshTokenException extends RuntimeException {

    private final RefreshTokenError error;

    public RefreshTokenException(RefreshTokenError error) {
        super("Refresh session cannot be used");
        this.error = error;
    }

    public RefreshTokenError error() {
        return error;
    }
}
