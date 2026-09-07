package com.github.stimur1709.cloudops.auth.api;

import com.github.stimur1709.cloudops.auth.application.RefreshTokenException;
import com.github.stimur1709.cloudops.common.api.error.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = AuthController.class)
public class AuthApiExceptionHandler {

    private final Clock clock;

    public AuthApiExceptionHandler(Clock clock) {
        this.clock = clock;
    }

    @ExceptionHandler(RefreshTokenException.class)
    public ResponseEntity<ApiError> handleRefreshToken(RefreshTokenException exception, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiError(
                        exception.error().code(),
                        exception.getMessage(),
                        clock.instant(),
                        request.getRequestURI(),
                        List.of()));
    }
}
