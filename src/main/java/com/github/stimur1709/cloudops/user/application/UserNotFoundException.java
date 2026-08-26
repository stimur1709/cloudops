package com.github.stimur1709.cloudops.user.application;

public final class UserNotFoundException extends RuntimeException {

    public UserNotFoundException(long id) {
        super("User not found: " + id);
    }
}
