package com.github.stimur1709.cloudops.common.api.error;

import java.time.Clock;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import com.github.stimur1709.cloudops.resource.application.ResourceNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import tools.jackson.core.JacksonException.Reference;
import tools.jackson.databind.exc.InvalidFormatException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final Clock clock;

    public GlobalExceptionHandler(Clock clock) {
        this.clock = clock;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> handleValidation(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        List<ApiFieldError> errors = exception.getBindingResult().getAllErrors().stream()
                .map(error -> new ApiFieldError(
                        error instanceof FieldError fieldError ? fieldError.getField() : null,
                        error.getDefaultMessage() == null ? "Invalid value" : error.getDefaultMessage()
                ))
                .sorted(Comparator.comparing(ApiFieldError::field, Comparator.nullsLast(String::compareTo))
                        .thenComparing(ApiFieldError::message))
                .toList();

        return response(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR",
                "Request validation failed",
                request,
                errors
        );
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiError> handleUnreadableBody(
            HttpMessageNotReadableException exception,
            HttpServletRequest request
    ) {
        InvalidFormatException invalidFormat = findInvalidFormat(exception);
        List<ApiFieldError> errors = invalidFormat == null ? List.of() : enumFieldError(invalidFormat);

        return response(
                HttpStatus.BAD_REQUEST,
                "INVALID_REQUEST",
                "Request body is invalid",
                request,
                errors
        );
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    ResponseEntity<ApiError> handleNotFound(HttpServletRequest request) {
        return response(
                HttpStatus.NOT_FOUND,
                "RESOURCE_NOT_FOUND",
                "Resource not found",
                request,
                List.of()
        );
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> handleUnexpected(Exception exception, HttpServletRequest request) {
        LOGGER.error("Unhandled exception while processing {}", request.getRequestURI(), exception);
        return response(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_ERROR",
                "An unexpected error occurred",
                request,
                List.of()
        );
    }

    private ResponseEntity<ApiError> response(
            HttpStatus status,
            String code,
            String message,
            HttpServletRequest request,
            List<ApiFieldError> errors
    ) {
        ApiError apiError = new ApiError(code, message, clock.instant(), request.getRequestURI(), errors);
        return ResponseEntity.status(status).body(apiError);
    }

    private InvalidFormatException findInvalidFormat(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof InvalidFormatException invalidFormat) {
                return invalidFormat;
            }
            current = current.getCause();
        }
        return null;
    }

    private List<ApiFieldError> enumFieldError(InvalidFormatException exception) {
        Class<?> targetType = exception.getTargetType();
        String field = exception.getPath().stream()
                .map(Reference::getPropertyName)
                .filter(propertyName -> propertyName != null && !propertyName.isBlank())
                .reduce((_, second) -> second)
                .orElse(null);

        if (field == null || targetType == null || !targetType.isEnum()) {
            return List.of();
        }

        String allowedValues = Arrays.stream(targetType.getEnumConstants())
                .map(Object::toString)
                .collect(Collectors.joining(", "));
        String displayName = Character.toUpperCase(field.charAt(0)) + field.substring(1);
        return List.of(new ApiFieldError(field, displayName + " must be one of: " + allowedValues));
    }
}

