package com.github.stimur1709.cloudops.auth.cleanup;

import java.sql.Timestamp;
import java.time.Instant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class RefreshTokenCleanupRepository {

    private static final String DELETE_SQL = """
            WITH removable AS (
                SELECT id
                FROM refresh_tokens
                WHERE expires_at <= ?
                   OR (revoked_at IS NOT NULL AND revoked_at <= ?)
                ORDER BY expires_at, id
                LIMIT ?
                FOR UPDATE SKIP LOCKED
            )
            DELETE FROM refresh_tokens AS token
            USING removable
            WHERE token.id = removable.id
            RETURNING token.id
            """;

    private final JdbcTemplate jdbcTemplate;

    RefreshTokenCleanupRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    int deleteBatch(Instant expiredCutoff, Instant revokedCutoff, int batchSize) {
        return jdbcTemplate
                .query(
                        DELETE_SQL,
                        (resultSet, _) -> resultSet.getLong("id"),
                        Timestamp.from(expiredCutoff),
                        Timestamp.from(revokedCutoff),
                        batchSize)
                .size();
    }
}
