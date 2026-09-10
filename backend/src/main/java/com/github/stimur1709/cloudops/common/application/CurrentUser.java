package com.github.stimur1709.cloudops.common.application;

import org.springframework.security.core.Authentication;

public final class CurrentUser {

    private CurrentUser() {}

    public static long id(Authentication authentication) {
        try {
            return Long.parseLong(authentication.getName());
        } catch (RuntimeException exception) {
            throw new ForbiddenException();
        }
    }
}
