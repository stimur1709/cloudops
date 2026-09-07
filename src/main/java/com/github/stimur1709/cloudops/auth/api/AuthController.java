package com.github.stimur1709.cloudops.auth.api;

import com.github.stimur1709.cloudops.auth.application.AuthService;
import com.github.stimur1709.cloudops.auth.application.AuthSession;
import com.github.stimur1709.cloudops.common.application.CurrentUser;
import com.github.stimur1709.cloudops.common.config.JwtProperties;
import com.github.stimur1709.cloudops.user.api.UserResponse;
import com.github.stimur1709.cloudops.user.application.UserService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final UserService userService;
    private final RefreshCookieFactory refreshCookieFactory;
    private final JwtProperties jwtProperties;

    public AuthController(
            AuthService authService,
            UserService userService,
            RefreshCookieFactory refreshCookieFactory,
            JwtProperties jwtProperties) {
        this.authService = authService;
        this.userService = userService;
        this.refreshCookieFactory = refreshCookieFactory;
        this.jwtProperties = jwtProperties;
    }

    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        UserResponse response =
                UserResponse.from(authService.register(request.email(), request.displayName(), request.password()));
        return ResponseEntity.created(URI.create("/api/users/" + response.id())).body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return session(authService.login(request.email(), request.password()));
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(HttpServletRequest request) {
        return session(authService.refresh(refreshToken(request)));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        authService.logout(refreshToken(request));
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, refreshCookieFactory.clear().toString())
                .build();
    }

    @GetMapping("/me")
    public UserResponse me(Authentication authentication) {
        return UserResponse.from(userService.get(CurrentUser.id(authentication)));
    }

    private ResponseEntity<TokenResponse> session(AuthSession session) {
        return ResponseEntity.ok()
                .header(
                        HttpHeaders.SET_COOKIE,
                        refreshCookieFactory
                                .create(session.refreshToken(), jwtProperties.refreshTokenTtl())
                                .toString())
                .body(session.response());
    }

    private String refreshToken(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return null;
        }
        for (Cookie cookie : request.getCookies()) {
            if (refreshCookieFactory.name().equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
