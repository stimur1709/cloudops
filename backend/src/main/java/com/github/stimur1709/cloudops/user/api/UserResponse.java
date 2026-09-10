package com.github.stimur1709.cloudops.user.api;

import com.github.stimur1709.cloudops.user.persistence.UserEntity;
import java.time.Instant;

public record UserResponse(long id, String email, String displayName, Instant createdAt, Instant updatedAt) {
    public static UserResponse from(UserEntity user) {
        return new UserResponse(user.id(), user.email(), user.displayName(), user.createdAt(), user.updatedAt());
    }
}
