package com.github.stimur1709.cloudops.resource.api.error;

import java.time.Instant;
import java.util.List;

public record ApiError(
        String code,
        String message,
        Instant timestamp,
        String path,
        List<ApiFieldError> errors
) {
}

