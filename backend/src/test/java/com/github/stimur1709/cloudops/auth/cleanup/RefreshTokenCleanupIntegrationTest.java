package com.github.stimur1709.cloudops.auth.cleanup;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.stimur1709.cloudops.TestcontainersConfiguration;
import java.sql.Timestamp;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class RefreshTokenCleanupIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RefreshTokenCleanupService service;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("""
                TRUNCATE TABLE refresh_tokens, resource_credentials, credentials, resource_probe_settings, organization_probe_settings, monitoring_results, monitors, resource_health_events, resource_health, outbox_messages, tasks, organization_memberships, resources, users, organizations RESTART IDENTITY
                """);
        jdbcTemplate.update("""
                INSERT INTO users (email, display_name, password_hash, created_at, updated_at)
                VALUES ('cleanup@example.com', 'Cleanup', 'hash', NOW(), NOW())
                """);
    }

    @Test
    void cleanupDeletesOnlyOneConfiguredBatchOfExpiredOrOldRevokedTokens() {
        Instant now = Instant.now();
        for (int index = 0; index < 51; index++) {
            insert("expired-" + index, now.minusSeconds(1), null);
        }
        insert("old-revoked", now.plusSeconds(3600), now.minusSeconds(8 * 24 * 3600));
        insert("recent-revoked", now.plusSeconds(3600), now.minusSeconds(60));
        insert("active", now.plusSeconds(3600), null);

        assertThat(service.deleteBatch()).isEqualTo(50);
        assertThat(count("SELECT COUNT(*) FROM refresh_tokens")).isEqualTo(4);
        assertThat(count("SELECT COUNT(*) FROM refresh_tokens WHERE token_hash IN ('recent-revoked', 'active')"))
                .isEqualTo(2);
    }

    private void insert(String hash, Instant expiresAt, Instant revokedAt) {
        jdbcTemplate.update("""
                INSERT INTO refresh_tokens (user_id, token_hash, expires_at, revoked_at, created_at)
                VALUES (1, ?, ?, ?, NOW())
                """, hash, Timestamp.from(expiresAt), revokedAt == null ? null : Timestamp.from(revokedAt));
    }

    private int count(String sql) {
        return jdbcTemplate.queryForObject(sql, Integer.class);
    }
}
