package com.github.stimur1709.cloudops.common.api.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiFieldError(
        @Schema(description = "Field path; omitted for an object-level violation")
        String field,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String message) {}
