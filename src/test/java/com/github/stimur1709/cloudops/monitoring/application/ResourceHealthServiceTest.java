package com.github.stimur1709.cloudops.monitoring.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.stimur1709.cloudops.monitoring.HealthStatus;
import com.github.stimur1709.cloudops.monitoring.ResourceHealthStatus;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ResourceHealthServiceTest {

    @ParameterizedTest
    @MethodSource("aggregations")
    void aggregatesEnabledMonitorHealth(List<HealthStatus> monitorStatuses, ResourceHealthStatus expected) {
        assertThat(ResourceHealthService.aggregate(monitorStatuses)).isEqualTo(expected);
    }

    private static Stream<Arguments> aggregations() {
        return Stream.of(
                Arguments.of(List.of(), ResourceHealthStatus.UNKNOWN),
                Arguments.of(List.of(HealthStatus.UNKNOWN), ResourceHealthStatus.UNKNOWN),
                Arguments.of(List.of(HealthStatus.UP), ResourceHealthStatus.UP),
                Arguments.of(List.of(HealthStatus.DOWN), ResourceHealthStatus.DOWN),
                Arguments.of(List.of(HealthStatus.UP, HealthStatus.UP), ResourceHealthStatus.UP),
                Arguments.of(List.of(HealthStatus.DOWN, HealthStatus.DOWN), ResourceHealthStatus.DOWN),
                Arguments.of(List.of(HealthStatus.UP, HealthStatus.DOWN), ResourceHealthStatus.DEGRADED),
                Arguments.of(
                        List.of(HealthStatus.UP, HealthStatus.DOWN, HealthStatus.UNKNOWN),
                        ResourceHealthStatus.UNKNOWN));
    }
}
