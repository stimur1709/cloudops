package com.github.stimur1709.cloudops.auth.application;

import com.github.stimur1709.cloudops.auth.api.TokenResponse;
import com.github.stimur1709.cloudops.auth.persistence.RefreshTokenEntity;
import com.github.stimur1709.cloudops.auth.persistence.RefreshTokenJpaRepository;
import com.github.stimur1709.cloudops.common.config.JwtProperties;
import com.github.stimur1709.cloudops.user.application.UserService;
import com.github.stimur1709.cloudops.user.persistence.UserEntity;
import com.github.stimur1709.cloudops.user.persistence.UserJpaRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final String DUMMY_PASSWORD_HASH =
            "{bcrypt}$2a$10$7EqJtq98hPqEX7fNZaFWoO5Cqyn3fJuV2ZRqtdSwd8kA9Q6ZFIQ1a";

    private final UserService userService;
    private final UserJpaRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenJpaRepository refreshTokenRepository;
    private final RefreshTokenCodec refreshTokenCodec;
    private final JwtEncoder jwtEncoder;
    private final JwtProperties jwtProperties;
    private final Clock clock;

    public AuthService(
            UserService userService,
            UserJpaRepository userRepository,
            PasswordEncoder passwordEncoder,
            RefreshTokenJpaRepository refreshTokenRepository,
            RefreshTokenCodec refreshTokenCodec,
            JwtEncoder jwtEncoder,
            JwtProperties jwtProperties,
            Clock clock) {
        this.userService = userService;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenRepository = refreshTokenRepository;
        this.refreshTokenCodec = refreshTokenCodec;
        this.jwtEncoder = jwtEncoder;
        this.jwtProperties = jwtProperties;
        this.clock = clock;
    }

    @Transactional
    public UserEntity register(String email, String displayName, String password) {
        return userService.register(email, displayName, passwordEncoder.encode(password));
    }

    @Transactional
    public AuthSession login(String email, String password) {
        UserEntity user =
                userRepository.findByEmail(UserEntity.normalizeEmail(email)).orElse(null);
        String storedHash = user == null ? DUMMY_PASSWORD_HASH : user.passwordHash();
        if (!passwordEncoder.matches(password, storedHash) || user == null) {
            throw new BadCredentialsException("Invalid credentials");
        }

        return createSession(user.id(), clock.instant()).session();
    }

    @Transactional
    public AuthSession refresh(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new RefreshTokenException(RefreshTokenError.INVALID);
        }

        RefreshTokenEntity current = refreshTokenRepository
                .findByTokenHashForUpdate(refreshTokenCodec.hash(rawRefreshToken))
                .orElseThrow(() -> new RefreshTokenException(RefreshTokenError.INVALID));
        Instant now = clock.instant();
        if (!current.expiresAt().isAfter(now)) {
            throw new RefreshTokenException(RefreshTokenError.EXPIRED);
        }
        if (current.revokedAt() != null) {
            throw new RefreshTokenException(RefreshTokenError.REVOKED);
        }

        CreatedSession replacement = createSession(current.userId(), now);
        current.rotate(now, replacement.refreshTokenId());
        return replacement.session();
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }
        refreshTokenRepository
                .findByTokenHashForUpdate(refreshTokenCodec.hash(rawRefreshToken))
                .ifPresent(token -> token.revoke(clock.instant()));
    }

    private CreatedSession createSession(long userId, Instant issuedAt) {
        Instant accessExpiresAt = issuedAt.plus(jwtProperties.accessTokenTtl());
        Instant refreshExpiresAt = issuedAt.plus(jwtProperties.refreshTokenTtl());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(Long.toString(userId))
                .issuer(jwtProperties.issuer())
                .issuedAt(issuedAt)
                .expiresAt(accessExpiresAt)
                .id(UUID.randomUUID().toString())
                .build();
        JwsHeader headers = JwsHeader.with(MacAlgorithm.HS256).build();
        String token =
                jwtEncoder.encode(JwtEncoderParameters.from(headers, claims)).getTokenValue();
        String refreshToken = refreshTokenCodec.generate();
        RefreshTokenEntity entity = refreshTokenRepository.saveAndFlush(
                RefreshTokenEntity.create(userId, refreshTokenCodec.hash(refreshToken), refreshExpiresAt, issuedAt));
        AuthSession session = new AuthSession(
                new TokenResponse(
                        token, "Bearer", jwtProperties.accessTokenTtl().toSeconds(), accessExpiresAt, refreshExpiresAt),
                refreshToken);
        return new CreatedSession(session, entity.id());
    }

    private record CreatedSession(AuthSession session, long refreshTokenId) {}
}
