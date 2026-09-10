package com.github.stimur1709.cloudops.common.api.error;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

public record ApiError(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String code,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String message,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant timestamp,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String path,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<ApiFieldError> errors) {}
