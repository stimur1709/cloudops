package com.github.stimur1709.cloudops.common.api.error;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiFieldError(String field, String message) {
}
