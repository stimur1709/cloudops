package com.github.stimur1709.cloudops.monitoring.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.github.stimur1709.cloudops.monitoring.ResourceHealthStatus;
import com.github.stimur1709.cloudops.monitoring.persistence.ResourceHealthEventEntity;
import org.junit.jupiter.api.Test;

class ResourceAvailabilityServiceTest {

    private static final Instant FROM = Instant.parse("2026-08-28T10:00:00Z");
    private static final Instant TO = Instant.parse("2026-08-28T11:00:00Z");

    @Test
    void returnsUnknownPeriodWithoutPercentagesWhenNoStateIsKnown() {
        ResourceAvailability result = calculate(ResourceHealthStatus.UNKNOWN, List.of());

        assertThat(result.periodSeconds()).isEqualTo(3600);
        assertThat(result.upSeconds()).isZero();
        assertThat(result.degradedSeconds()).isZero();
        assertThat(result.downSeconds()).isZero();
        assertThat(result.unknownSeconds()).isEqualTo(3600);
        assertThat(result.knownSeconds()).isZero();
        assertThat(result.uptimePercent()).isNull();
        assertThat(result.availabilityPercent()).isNull();
        assertThat(result.coveragePercent()).isEqualByComparingTo("0.00");
    }

    @Test
    void calculatesWholePeriodForEveryKnownStatus() {
        assertWholePeriod(ResourceHealthStatus.UP, 3600, 0, 0, "100.00", "100.00");
        assertWholePeriod(ResourceHealthStatus.DOWN, 0, 0, 3600, "0.00", "0.00");
        assertWholePeriod(ResourceHealthStatus.DEGRADED, 0, 3600, 0, "0.00", "100.00");
    }

    @Test
    void calculatesUpDownUpSequence() {
        ResourceAvailability result = calculate(ResourceHealthStatus.UP, List.of(
                event(ResourceHealthStatus.UP, ResourceHealthStatus.DOWN, "2026-08-28T10:20:00Z"),
                event(ResourceHealthStatus.DOWN, ResourceHealthStatus.UP, "2026-08-28T10:35:00Z")
        ));

        assertThat(result.upSeconds()).isEqualTo(2700);
        assertThat(result.downSeconds()).isEqualTo(900);
        assertThat(result.knownSeconds()).isEqualTo(3600);
        assertThat(result.uptimePercent()).isEqualByComparingTo("75.00");
        assertThat(result.availabilityPercent()).isEqualByComparingTo("75.00");
        assertDurationsCoverPeriod(result);
    }

    @Test
    void keepsDegradedSeparateAndIncludesItOnlyInAvailability() {
        ResourceAvailability result = calculate(ResourceHealthStatus.UP, List.of(
                event(ResourceHealthStatus.UP, ResourceHealthStatus.DEGRADED, "2026-08-28T10:15:00Z"),
                event(ResourceHealthStatus.DEGRADED, ResourceHealthStatus.DOWN, "2026-08-28T10:30:00Z"),
                event(ResourceHealthStatus.DOWN, ResourceHealthStatus.UP, "2026-08-28T10:45:00Z")
        ));

        assertThat(result.upSeconds()).isEqualTo(1800);
        assertThat(result.degradedSeconds()).isEqualTo(900);
        assertThat(result.downSeconds()).isEqualTo(900);
        assertThat(result.uptimePercent()).isEqualByComparingTo("50.00");
        assertThat(result.availabilityPercent()).isEqualByComparingTo("75.00");
        assertThat(result.coveragePercent()).isEqualByComparingTo("100.00");
        assertDurationsCoverPeriod(result);
    }

    @Test
    void excludesUnknownTimeBeforeFirstKnownStateFromKnownDenominator() {
        ResourceAvailability result = calculate(ResourceHealthStatus.UNKNOWN, List.of(
                event(ResourceHealthStatus.UNKNOWN, ResourceHealthStatus.UP, "2026-08-28T10:10:00Z"),
                event(ResourceHealthStatus.UP, ResourceHealthStatus.DOWN, "2026-08-28T10:40:00Z")
        ));

        assertThat(result.unknownSeconds()).isEqualTo(600);
        assertThat(result.upSeconds()).isEqualTo(1800);
        assertThat(result.downSeconds()).isEqualTo(1200);
        assertThat(result.knownSeconds()).isEqualTo(3000);
        assertThat(result.uptimePercent()).isEqualByComparingTo("60.00");
        assertThat(result.availabilityPercent()).isEqualByComparingTo("60.00");
        assertThat(result.coveragePercent()).isEqualByComparingTo("83.33");
        assertDurationsCoverPeriod(result);
    }

    @Test
    void roundedStatusDurationsStillCoverPeriodWithFractionalTransitions() {
        Instant from = Instant.parse("2026-08-28T10:00:00Z");
        Instant to = Instant.parse("2026-08-28T10:00:02Z");
        ResourceAvailability result = ResourceAvailabilityService.calculate(
                from,
                to,
                ResourceHealthStatus.UP,
                List.of(
                        event(ResourceHealthStatus.UP, ResourceHealthStatus.DOWN, "2026-08-28T10:00:00.600Z"),
                        event(ResourceHealthStatus.DOWN, ResourceHealthStatus.UP, "2026-08-28T10:00:01.200Z")
                )
        );

        assertThat(result.periodSeconds()).isEqualTo(2);
        assertThat(result.upSeconds()).isEqualTo(1);
        assertThat(result.downSeconds()).isEqualTo(1);
        assertDurationsCoverPeriod(result);
    }

    private ResourceAvailability calculate(
            ResourceHealthStatus initialStatus,
            List<ResourceHealthEventEntity> events
    ) {
        return ResourceAvailabilityService.calculate(FROM, TO, initialStatus, events);
    }

    private void assertWholePeriod(
            ResourceHealthStatus status,
            long upSeconds,
            long degradedSeconds,
            long downSeconds,
            String uptimePercent,
            String availabilityPercent
    ) {
        ResourceAvailability result = calculate(status, List.of());

        assertThat(result.upSeconds()).isEqualTo(upSeconds);
        assertThat(result.degradedSeconds()).isEqualTo(degradedSeconds);
        assertThat(result.downSeconds()).isEqualTo(downSeconds);
        assertThat(result.unknownSeconds()).isZero();
        assertThat(result.knownSeconds()).isEqualTo(3600);
        assertThat(result.uptimePercent()).isEqualByComparingTo(new BigDecimal(uptimePercent));
        assertThat(result.availabilityPercent()).isEqualByComparingTo(new BigDecimal(availabilityPercent));
        assertThat(result.coveragePercent()).isEqualByComparingTo("100.00");
        assertDurationsCoverPeriod(result);
    }

    private void assertDurationsCoverPeriod(ResourceAvailability result) {
        assertThat(result.upSeconds() + result.degradedSeconds() + result.downSeconds() + result.unknownSeconds())
                .isEqualTo(result.periodSeconds());
    }

    private ResourceHealthEventEntity event(
            ResourceHealthStatus from,
            ResourceHealthStatus to,
            String changedAt
    ) {
        return ResourceHealthEventEntity.create(1L, from, to, Instant.parse(changedAt));
    }
}
