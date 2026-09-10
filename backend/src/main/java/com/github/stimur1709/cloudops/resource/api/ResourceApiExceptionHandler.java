package com.github.stimur1709.cloudops.resource.api;

import com.github.stimur1709.cloudops.common.api.error.ApiError;
import com.github.stimur1709.cloudops.common.api.error.ApiFieldError;
import com.github.stimur1709.cloudops.resource.ResourceType;
import com.github.stimur1709.cloudops.resource.config.ResourceConfig;
import com.github.stimur1709.cloudops.resource.config.UnknownResourceConfigFieldException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import tools.jackson.core.JacksonException.Reference;
import tools.jackson.databind.exc.InvalidFormatException;
import tools.jackson.databind.exc.InvalidTypeIdException;
import tools.jackson.databind.exc.MismatchedInputException;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = ResourceController.class)
public class ResourceApiExceptionHandler {

    private final Clock clock;

    public ResourceApiExceptionHandler(Clock clock) {
        this.clock = clock;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiError> handleUnreadableBody(
            HttpMessageNotReadableException exception, HttpServletRequest request) {
        InvalidFormatException invalidFormat = findCause(exception, InvalidFormatException.class);
        InvalidTypeIdException invalidTypeId = findCause(exception, InvalidTypeIdException.class);
        MismatchedInputException mismatchedInput = findCause(exception, MismatchedInputException.class);
        UnknownResourceConfigFieldException unknownField =
                findCause(exception, UnknownResourceConfigFieldException.class);

        List<ApiFieldError> errors;
        if (unknownField != null) {
            errors = List.of(new ApiFieldError("config." + unknownField.field(), "Unknown field"));
        } else if (isResourceConfigTypeId(invalidTypeId)) {
            String values = Arrays.stream(ResourceType.values()).map(Enum::name).collect(Collectors.joining(", "));
            errors = List.of(new ApiFieldError("type", "Type must be one of: " + values));
        } else if (invalidFormat != null) {
            errors = enumFieldError(invalidFormat);
        } else if (mismatchedInput != null && mismatchedInput.getMessage().contains("'config'")) {
            errors = List.of(new ApiFieldError("config", "Config is required"));
        } else {
            errors = List.of();
        }

        return ResponseEntity.badRequest()
                .body(new ApiError(
                        "INVALID_REQUEST",
                        "Request body is invalid",
                        clock.instant(),
                        request.getRequestURI(),
                        errors));
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
        String displayName = Character.toUpperCase(field.charAt(0)) + field.substring(1);
        return List.of(new ApiFieldError(field, displayName + " must be one of: " + allowedValues));
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
}
