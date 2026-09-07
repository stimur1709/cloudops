package com.github.stimur1709.cloudops.auth.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "refresh_tokens")
public class RefreshTokenEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "token_hash", nullable = false, length = 43, unique = true)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "replaced_by")
    private Long replacedBy;

    protected RefreshTokenEntity() {}

    private RefreshTokenEntity(long userId, String tokenHash, Instant expiresAt, Instant createdAt) {
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.createdAt = createdAt;
    }

    public static RefreshTokenEntity create(long userId, String tokenHash, Instant expiresAt, Instant createdAt) {
        return new RefreshTokenEntity(userId, tokenHash, expiresAt, createdAt);
    }

    public void rotate(Instant revokedAt, long replacementId) {
        this.revokedAt = revokedAt;
        this.replacedBy = replacementId;
    }

    public void revoke(Instant revokedAt) {
        if (this.revokedAt == null) {
            this.revokedAt = revokedAt;
        }
    }

    public Long id() {
        return id;
    }

    public Long userId() {
        return userId;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public Instant revokedAt() {
        return revokedAt;
    }
}
