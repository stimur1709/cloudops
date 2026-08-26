package com.github.stimur1709.cloudops.common.api.error;

import java.time.Clock;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import com.github.stimur1709.cloudops.common.search.InvalidSearchException;
import com.github.stimur1709.cloudops.resource.application.ResourceNotFoundException;
import com.github.stimur1709.cloudops.resource.application.ResourceNameConflictException;
import com.github.stimur1709.cloudops.organization.application.OrganizationInUseException;
import com.github.stimur1709.cloudops.organization.application.OrganizationNotFoundException;
import com.github.stimur1709.cloudops.membership.application.LastOwnerException;
import com.github.stimur1709.cloudops.membership.application.MembershipConflictException;
import com.github.stimur1709.cloudops.membership.application.MembershipNotFoundException;
import com.github.stimur1709.cloudops.user.application.UserEmailConflictException;
import com.github.stimur1709.cloudops.user.application.UserInUseException;
import com.github.stimur1709.cloudops.user.application.UserNotFoundException;
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
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
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
        List<ApiFieldError> errors = invalidFormat == null ? List.of() : enumFieldError(invalidFormat);

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

    @ExceptionHandler(OrganizationNotFoundException.class)
    ResponseEntity<ApiError> handleOrganizationNotFound(HttpServletRequest request) {
        return response(
                HttpStatus.NOT_FOUND,
                "ORGANIZATION_NOT_FOUND",
                "Organization not found",
                request,
                List.of()
        );
    }

    @ExceptionHandler(ResourceNameConflictException.class)
    ResponseEntity<ApiError> handleResourceNameConflict(HttpServletRequest request) {
        return response(
                HttpStatus.CONFLICT,
                "RESOURCE_NAME_CONFLICT",
                "Resource name is already used in this organization",
                request,
                List.of()
        );
    }

    @ExceptionHandler(OrganizationInUseException.class)
    ResponseEntity<ApiError> handleOrganizationInUse(HttpServletRequest request) {
        return response(
                HttpStatus.CONFLICT,
                "ORGANIZATION_IN_USE",
                "Organization cannot be deleted while it has resources or members",
                request,
                List.of()
        );
    }

    @ExceptionHandler(UserNotFoundException.class)
    ResponseEntity<ApiError> handleUserNotFound(HttpServletRequest request) {
        return response(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found", request, List.of());
    }

    @ExceptionHandler(MembershipNotFoundException.class)
    ResponseEntity<ApiError> handleMembershipNotFound(HttpServletRequest request) {
        return response(HttpStatus.NOT_FOUND, "MEMBERSHIP_NOT_FOUND", "Membership not found", request, List.of());
    }

    @ExceptionHandler(UserEmailConflictException.class)
    ResponseEntity<ApiError> handleUserEmailConflict(HttpServletRequest request) {
        return response(
                HttpStatus.CONFLICT,
                "USER_EMAIL_CONFLICT",
                "Email is already used by another user",
                request,
                List.of()
        );
    }

    @ExceptionHandler(UserInUseException.class)
    ResponseEntity<ApiError> handleUserInUse(HttpServletRequest request) {
        return response(
                HttpStatus.CONFLICT,
                "USER_IN_USE",
                "User cannot be deleted while they belong to an organization",
                request,
                List.of()
        );
    }

    @ExceptionHandler(MembershipConflictException.class)
    ResponseEntity<ApiError> handleMembershipConflict(HttpServletRequest request) {
        return response(
                HttpStatus.CONFLICT,
                "MEMBERSHIP_CONFLICT",
                "User is already a member of this organization",
                request,
                List.of()
        );
    }

    @ExceptionHandler(LastOwnerException.class)
    ResponseEntity<ApiError> handleLastOwner(HttpServletRequest request) {
        return response(
                HttpStatus.CONFLICT,
                "LAST_OWNER_REQUIRED",
                "Organization must have at least one owner",
                request,
                List.of()
        );
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
