package com.github.stimur1709.cloudops.monitoring.settings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;

import com.github.stimur1709.cloudops.TestAuthentication;
import com.github.stimur1709.cloudops.TestcontainersConfiguration;
import com.github.stimur1709.cloudops.monitoring.StorageMode;
import com.github.stimur1709.cloudops.monitoring.application.ResourceHealthService;
import com.github.stimur1709.cloudops.monitoring.settings.api.ProbeSettingsRequest;
import com.github.stimur1709.cloudops.monitoring.settings.application.MonitoringSettingsRecovery;
import com.github.stimur1709.cloudops.monitoring.settings.application.MonitoringSettingsSynchronizer;
import com.github.stimur1709.cloudops.monitoring.settings.application.ProbeSettingsService;
import com.github.stimur1709.cloudops.probe.ProbeType;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "cloudops.monitoring.settings-recovery-interval=24h")
class MonitoringSettingsRecoveryIntegrationTest {
    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MonitoringSettingsIndex index;

    @Autowired
    private MonitoringSettingsRecovery recovery;

    @Autowired
    private ProbeSettingsService settingsService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockitoSpyBean
    private MonitoringSettingsSynchronizer synchronizer;

    @MockitoSpyBean
    private ResourceHealthService healthService;

    private long organizationId;
    private long resourceId;
    private long monitorId;

    @BeforeEach
    void setUp() {
        recovery.retryPending();
        jdbc.execute("""
                TRUNCATE TABLE resource_credentials, credentials, resource_probe_settings, organization_probe_settings,
                    monitoring_results, monitors, resource_health_events, resource_health, outbox_messages, tasks,
                    organization_memberships, resources, users, organizations RESTART IDENTITY
                """);
        index.reload();
        jdbc.update("""
                INSERT INTO users (id, email, display_name, password_hash, created_at, updated_at)
                VALUES (?, 'recovery@example.com', 'Recovery', '{noop}unused', NOW(), NOW())
                """, TestAuthentication.USER_ID);
        organizationId = jdbc.queryForObject("""
                INSERT INTO organizations (name, created_at, updated_at)
                VALUES ('Recovery', NOW(), NOW()) RETURNING id
                """, Long.class);
        jdbc.update("""
                INSERT INTO organization_memberships (organization_id, user_id, role, created_at, updated_at)
                VALUES (?, ?, 'OWNER', NOW(), NOW())
                """, organizationId, TestAuthentication.USER_ID);
        resourceId = jdbc.queryForObject("""
                INSERT INTO resources (name, type, status, organization_id, config, created_at, updated_at)
                VALUES ('api', 'SERVICE', 'ACTIVE', ?, '{"url":"https://example.com"}'::jsonb, NOW(), NOW()) RETURNING id
                """, Long.class, organizationId);
        jdbc.update("INSERT INTO resource_health (resource_id, health_status) VALUES (?, 'UP')", resourceId);
        monitorId = jdbc.queryForObject("""
                INSERT INTO monitors (resource_id, type, next_run_at, health_status)
                VALUES (?, 'HTTP_CHECK', NOW() + INTERVAL '1 hour', 'UP') RETURNING id
                """, Long.class, resourceId);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void retriesFailedPutAndDeleteFromCommittedDatabaseValues(boolean organization) {
        failSynchronization(organization, false);
        put(organization, false, 41);
        assertThat(override(organization)).isNull();
        assertThat(jdbc.queryForObject("SELECT enabled FROM " + table(organization), Boolean.class))
                .isFalse();
        assertThat(nextRunAt()).isNotNull();

        failSynchronization(organization, true);
        recovery.retryPending();
        assertThat(override(organization)).isNull();
        restoreSynchronization(organization, true);
        recovery.retryPending();
        assertThat(override(organization).enabled()).isFalse();
        assertThat(override(organization).intervalSeconds()).isEqualTo(41);
        assertThat(nextRunAt()).isNull();
        assertThat(jdbc.queryForObject(
                        "SELECT health_status FROM resource_health WHERE resource_id = ?", String.class, resourceId))
                .isEqualTo("UNKNOWN");

        if (organization) {
            settingsService.deleteOrganization(organizationId, ProbeType.HTTP_CHECK, TestAuthentication.USER_ID);
        } else {
            settingsService.deleteResource(resourceId, ProbeType.HTTP_CHECK, TestAuthentication.USER_ID);
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM " + table(organization), Integer.class))
                .isZero();
        assertThat(override(organization)).isNotNull();
        recovery.retryPending();
        assertThat(override(organization)).isNull();
        assertThat(nextRunAt()).isNotNull();
    }

    @Test
    void failureAfterIndexUpdateRollsBackScheduleAndRecoveryPreservesHealthyCadence() {
        Instant scheduled = nextRunAt();
        doThrow(new IllegalStateException("injected reconciliation failure"))
                .when(healthService)
                .recalculate(resourceId);
        put(false, false, 41);
        assertThat(index.resource(resourceId, ProbeType.HTTP_CHECK).enabled()).isFalse();
        assertThat(nextRunAt()).isEqualTo(scheduled);
        doCallRealMethod().when(healthService).recalculate(resourceId);
        recovery.retryPending();
        assertThat(nextRunAt()).isNull();

        put(false, true, 52);
        jdbc.update("UPDATE monitors SET next_run_at = NOW() + INTERVAL '1 hour' WHERE id = ?", monitorId);
        scheduled = nextRunAt();
        doThrow(new IllegalStateException("injected reconciliation failure"))
                .when(healthService)
                .recalculate(resourceId);
        put(false, true, 63);
        assertThat(nextRunAt()).isEqualTo(scheduled);
        doCallRealMethod().when(healthService).recalculate(resourceId);
        recovery.retryPending();
        assertThat(index.resource(resourceId, ProbeType.HTTP_CHECK).intervalSeconds())
                .isEqualTo(63);
        assertThat(nextRunAt()).isEqualTo(scheduled);
    }

    @Test
    void newerSuccessfulMutationSupersedesPendingRecovery() {
        failSynchronization(false, false);
        put(false, false, 41);
        restoreSynchronization(false, false);
        put(false, true, 52);
        Instant scheduled = nextRunAt();
        recovery.retryPending();
        assertThat(index.resource(resourceId, ProbeType.HTTP_CHECK).intervalSeconds())
                .isEqualTo(52);
        assertThat(index.resource(resourceId, ProbeType.HTTP_CHECK).enabled()).isTrue();
        assertThat(nextRunAt()).isEqualTo(scheduled);
    }

    @Test
    void rolledBackMutationNeverUpdatesRuntimeOrSchedulesRecovery() {
        Instant scheduled = nextRunAt();
        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(_ -> {
                    put(false, false, 41);
                    throw new IllegalStateException("rollback");
                }))
                .isInstanceOf(IllegalStateException.class);
        recovery.retryPending();
        assertThat(index.resource(resourceId, ProbeType.HTTP_CHECK)).isNull();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM resource_probe_settings", Integer.class))
                .isZero();
        assertThat(nextRunAt()).isEqualTo(scheduled);
    }

    @Test
    void concurrentCommitDuringRecoveryLeavesNewestDatabaseSettingsInRuntime() throws Exception {
        failSynchronization(false, false);
        put(false, false, 41);
        restoreSynchronization(false, false);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch resume = new CountDownLatch(1);
        doAnswer(invocation -> {
                    entered.countDown();
                    assertThat(resume.await(10, TimeUnit.SECONDS)).isTrue();
                    return invocation.callRealMethod();
                })
                .when(synchronizer)
                .synchronizeResource(resourceId, ProbeType.HTTP_CHECK, true);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var retry = executor.submit(recovery::retryPending);
            try {
                assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
                var mutation = executor.submit(() -> put(false, true, 52));
                await().atMost(Duration.ofSeconds(10))
                        .untilAsserted(() -> assertThat(jdbc.queryForObject(
                                        "SELECT interval_seconds FROM resource_probe_settings WHERE resource_id = ?",
                                        Integer.class,
                                        resourceId))
                                .isEqualTo(52));
                resume.countDown();
                retry.get(10, TimeUnit.SECONDS);
                mutation.get(10, TimeUnit.SECONDS);
            } finally {
                resume.countDown();
            }
        }
        Instant scheduled = nextRunAt();
        recovery.retryPending();
        assertThat(index.resource(resourceId, ProbeType.HTTP_CHECK).intervalSeconds())
                .isEqualTo(52);
        assertThat(index.resource(resourceId, ProbeType.HTTP_CHECK).enabled()).isTrue();
        assertThat(nextRunAt()).isEqualTo(scheduled);
    }

    private void put(boolean organization, boolean enabled, int interval) {
        var request = new ProbeSettingsRequest(enabled, interval, 3, 2, StorageMode.LATEST_ONLY, null, 500);
        if (organization) {
            settingsService.putOrganization(organizationId, ProbeType.HTTP_CHECK, request, TestAuthentication.USER_ID);
        } else {
            settingsService.putResource(resourceId, ProbeType.HTTP_CHECK, request, TestAuthentication.USER_ID);
        }
    }

    private void failSynchronization(boolean organization, boolean retry) {
        var stub = doThrow(new IllegalStateException("injected post-commit failure"))
                .when(synchronizer);
        if (organization) stub.synchronizeOrganization(organizationId, ProbeType.HTTP_CHECK, retry);
        else stub.synchronizeResource(resourceId, ProbeType.HTTP_CHECK, retry);
    }

    private void restoreSynchronization(boolean organization, boolean retry) {
        var stub = doCallRealMethod().when(synchronizer);
        if (organization) stub.synchronizeOrganization(organizationId, ProbeType.HTTP_CHECK, retry);
        else stub.synchronizeResource(resourceId, ProbeType.HTTP_CHECK, retry);
    }

    private ProbeSettings override(boolean organization) {
        return organization
                ? index.organization(organizationId, ProbeType.HTTP_CHECK)
                : index.resource(resourceId, ProbeType.HTTP_CHECK);
    }

    private String table(boolean organization) {
        return organization ? "organization_probe_settings" : "resource_probe_settings";
    }

    private Instant nextRunAt() {
        return jdbc.queryForObject("SELECT next_run_at FROM monitors WHERE id = ?", Instant.class, monitorId);
    }
}
