package com.github.stimur1709.cloudops.auth.application;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import com.github.stimur1709.cloudops.auth.api.TokenResponse;
import com.github.stimur1709.cloudops.common.config.JwtProperties;
import com.github.stimur1709.cloudops.user.application.UserService;
import com.github.stimur1709.cloudops.user.persistence.UserEntity;
import com.github.stimur1709.cloudops.user.persistence.UserJpaRepository;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final String DUMMY_PASSWORD_HASH =
            "{bcrypt}$2a$10$7EqJtq98hPqEX7fNZaFWoO5Cqyn3fJuV2ZRqtdSwd8kA9Q6ZFIQ1a";

    private final UserService userService;
    private final UserJpaRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;
    private final JwtProperties jwtProperties;
    private final Clock clock;

    public AuthService(
            UserService userService,
            UserJpaRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtEncoder jwtEncoder,
            JwtProperties jwtProperties,
            Clock clock
    ) {
        this.userService = userService;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtEncoder = jwtEncoder;
        this.jwtProperties = jwtProperties;
        this.clock = clock;
    }

    @Transactional
    public UserEntity register(String email, String displayName, String password) {
        return userService.register(email, displayName, passwordEncoder.encode(password));
    }

    @Transactional(readOnly = true)
    public TokenResponse login(String email, String password) {
        UserEntity user = userRepository.findByEmail(UserEntity.normalizeEmail(email)).orElse(null);
        String storedHash = user == null ? DUMMY_PASSWORD_HASH : user.passwordHash();
        if (!passwordEncoder.matches(password, storedHash) || user == null) {
            throw new BadCredentialsException("Invalid credentials");
        }

        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plus(jwtProperties.accessTokenTtl());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(Long.toString(user.id()))
                .issuer(jwtProperties.issuer())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .id(UUID.randomUUID().toString())
                .build();
        JwsHeader headers = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(headers, claims)).getTokenValue();
        return new TokenResponse(token, "Bearer", jwtProperties.accessTokenTtl().toSeconds());
    }
}
