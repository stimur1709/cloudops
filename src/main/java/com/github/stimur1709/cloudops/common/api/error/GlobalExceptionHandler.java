package com.github.stimur1709.cloudops.common.api.error;

import java.time.Clock;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import com.github.stimur1709.cloudops.common.application.ConflictException;
import com.github.stimur1709.cloudops.common.application.ForbiddenException;
import com.github.stimur1709.cloudops.common.application.InvalidRequestException;
import com.github.stimur1709.cloudops.common.application.NotFoundException;
import com.github.stimur1709.cloudops.common.search.InvalidSearchException;
import com.github.stimur1709.cloudops.resource.ResourceType;
import com.github.stimur1709.cloudops.resource.config.ResourceConfig;
import com.github.stimur1709.cloudops.resource.config.UnknownResourceConfigFieldException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.security.core.AuthenticationException;
import tools.jackson.core.JacksonException.Reference;
import tools.jackson.databind.exc.InvalidFormatException;
import tools.jackson.databind.exc.InvalidTypeIdException;
import tools.jackson.databind.exc.MismatchedInputException;

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

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ApiError> handleConstraintViolation(
            ConstraintViolationException exception,
            HttpServletRequest request
    ) {
        List<ApiFieldError> errors = exception.getConstraintViolations().stream()
                .map(violation -> {
                    String path = violation.getPropertyPath().toString();
                    int separator = path.lastIndexOf('.');
                    String field = separator < 0 ? path : path.substring(separator + 1);
                    return new ApiFieldError(field, violation.getMessage());
                })
                .sorted(Comparator.comparing(ApiFieldError::field).thenComparing(ApiFieldError::message))
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
        MismatchedInputException mismatchedInput = findCause(exception, MismatchedInputException.class);
        InvalidTypeIdException invalidTypeId = findCause(exception, InvalidTypeIdException.class);
        UnknownResourceConfigFieldException unknownConfigField = findCause(
                exception, UnknownResourceConfigFieldException.class
        );
        List<ApiFieldError> errors;
        if (unknownConfigField != null) {
            errors = List.of(new ApiFieldError("config." + unknownConfigField.field(), "Unknown field"));
        } else if (isResourceConfigTypeId(invalidTypeId)) {
            String values = Arrays.stream(ResourceType.values())
                    .map(Enum::name)
                    .collect(Collectors.joining(", "));
            errors = List.of(new ApiFieldError("type", "Type must be one of: " + values));
        } else if (invalidFormat != null) {
            errors = enumFieldError(invalidFormat);
        } else if (mismatchedInput != null && mismatchedInput.getMessage().contains("'config'")) {
            errors = List.of(new ApiFieldError("config", "Config is required"));
        } else {
            errors = List.of();
        }

        return response(
                HttpStatus.BAD_REQUEST,
                "INVALID_REQUEST",
                "Request body is invalid",
                request,
                errors
        );
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ApiError> handleTypeMismatch(
            MethodArgumentTypeMismatchException exception,
            HttpServletRequest request
    ) {
        String field = exception.getName();
        String message = isNumeric(exception.getRequiredType())
                ? "%s must be a number".formatted(capitalize(field))
                : "%s has an invalid format".formatted(capitalize(field));

        return response(
                HttpStatus.BAD_REQUEST,
                "INVALID_REQUEST",
                "Request parameter is invalid",
                request,
                List.of(new ApiFieldError(field, message))
        );
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    ResponseEntity<ApiError> handleMissingRequestParameter(
            MissingServletRequestParameterException exception,
            HttpServletRequest request
    ) {
        String field = exception.getParameterName();
        return response(
                HttpStatus.BAD_REQUEST,
                "INVALID_REQUEST",
                "Request parameter is missing",
                request,
                List.of(new ApiFieldError(field, capitalize(field) + " is required"))
        );
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ApiError> handleMethodNotAllowed(
            HttpRequestMethodNotSupportedException exception,
            HttpServletRequest request
    ) {
        ApiError apiError = error(
                "METHOD_NOT_ALLOWED",
                "HTTP method is not supported for this endpoint",
                request,
                List.of()
        );

        return ResponseEntity
                .status(HttpStatus.METHOD_NOT_ALLOWED)
                .headers(exception.getHeaders())
                .body(apiError);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ApiError> handleUnsupportedMediaType(
            HttpMediaTypeNotSupportedException exception,
            HttpServletRequest request
    ) {
        ApiError apiError = error(
                "UNSUPPORTED_MEDIA_TYPE",
                "Content-Type is not supported",
                request,
                List.of()
        );

        return ResponseEntity
                .status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .headers(exception.getHeaders())
                .body(apiError);
    }

    @ExceptionHandler(NotFoundException.class)
    ResponseEntity<ApiError> handleNotFound(NotFoundException exception, HttpServletRequest request) {
        return response(
                HttpStatus.NOT_FOUND,
                exception.code(),
                exception.getMessage(),
                request,
                List.of()
        );
    }

    @ExceptionHandler(ConflictException.class)
    ResponseEntity<ApiError> handleConflict(ConflictException exception, HttpServletRequest request) {
        return response(
                HttpStatus.CONFLICT,
                exception.code(),
                exception.getMessage(),
                request,
                List.of()
        );
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ApiError> handleAuthentication(HttpServletRequest request) {
        return response(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Invalid email or password", request, List.of());
    }

    @ExceptionHandler(ForbiddenException.class)
    ResponseEntity<ApiError> handleForbidden(ForbiddenException exception, HttpServletRequest request) {
        return response(HttpStatus.FORBIDDEN, "FORBIDDEN", exception.getMessage(), request, List.of());
    }

    @ExceptionHandler(InvalidSearchException.class)
    ResponseEntity<ApiError> handleInvalidSearch(
            InvalidSearchException exception,
            HttpServletRequest request
    ) {
        return response(
                HttpStatus.BAD_REQUEST,
                "INVALID_REQUEST",
                "Search request is invalid",
                request,
                List.of(new ApiFieldError(exception.field(), exception.getMessage()))
        );
    }

    @ExceptionHandler(InvalidRequestException.class)
    ResponseEntity<ApiError> handleInvalidRequest(
            InvalidRequestException exception,
            HttpServletRequest request
    ) {
        return response(
                HttpStatus.BAD_REQUEST,
                "INVALID_REQUEST",
                "Request parameters are invalid",
                request,
                List.of(new ApiFieldError(exception.field(), exception.getMessage()))
        );
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ApiError> handleEndpointNotFound(HttpServletRequest request) {
        return response(
                HttpStatus.NOT_FOUND,
                "ENDPOINT_NOT_FOUND",
                "Endpoint not found",
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
        return ResponseEntity.status(status).body(error(code, message, request, errors));
    }

    private ApiError error(
            String code,
            String message,
            HttpServletRequest request,
            List<ApiFieldError> errors
    ) {
        return new ApiError(code, message, clock.instant(), request.getRequestURI(), errors);
    }

    private InvalidFormatException findInvalidFormat(Throwable throwable) {
        return findCause(throwable, InvalidFormatException.class);
    }

    private <T extends Throwable> T findCause(Throwable throwable, Class<T> type) {
        Throwable current = throwable;
        while (current != null) {
            if (type.isInstance(current)) {
                return type.cast(current);
            }
            current = current.getCause();
        }
        return null;
    }

    private boolean isResourceConfigTypeId(InvalidTypeIdException exception) {
        return exception != null
                && exception.getBaseType() != null
                && ResourceConfig.class.isAssignableFrom(exception.getBaseType().getRawClass());
    }

    private List<ApiFieldError> enumFieldError(InvalidFormatException exception) {
        Class<?> targetType = exception.getTargetType();
        String field = exception.getPath().stream()
                .map(Reference::getPropertyName)
                .filter(propertyName -> propertyName != null && !propertyName.isBlank())
                .collect(Collectors.joining("."));

        if (field.isBlank() || targetType == null || !targetType.isEnum()) {
            return List.of();
        }

        String allowedValues = Arrays.stream(targetType.getEnumConstants())
                .map(value -> ((Enum<?>) value).name())
                .collect(Collectors.joining(", "));
        String displayName = capitalize(field);
        return List.of(new ApiFieldError(field, displayName + " must be one of: " + allowedValues));
    }

    private boolean isNumeric(Class<?> type) {
        if (type == null) {
            return false;
        }
        if (type.isPrimitive()) {
            return type != boolean.class && type != char.class && type != void.class;
        }
        return Number.class.isAssignableFrom(type);
    }

    private String capitalize(String value) {
        if (value == null || value.isBlank()) {
            return "Value";
        }
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }
}
