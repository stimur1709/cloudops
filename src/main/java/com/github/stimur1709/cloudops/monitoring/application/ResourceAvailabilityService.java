package com.github.stimur1709.cloudops.monitoring.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;

import com.github.stimur1709.cloudops.common.application.NotFoundException;
import com.github.stimur1709.cloudops.membership.application.OrganizationAuthorization;
import com.github.stimur1709.cloudops.monitoring.ResourceHealthStatus;
import com.github.stimur1709.cloudops.monitoring.persistence.ResourceHealthEventEntity;
import com.github.stimur1709.cloudops.monitoring.persistence.ResourceHealthEventJpaRepository;
import com.github.stimur1709.cloudops.resource.persistence.ResourceEntity;
import com.github.stimur1709.cloudops.resource.persistence.ResourceJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ResourceAvailabilityService {

    private static final int PERCENT_SCALE = 2;

    private final ResourceJpaRepository resourceRepository;
    private final ResourceHealthEventJpaRepository eventRepository;
    private final OrganizationAuthorization authorization;

    public ResourceAvailabilityService(
            ResourceJpaRepository resourceRepository,
            ResourceHealthEventJpaRepository eventRepository,
            OrganizationAuthorization authorization
    ) {
        this.resourceRepository = resourceRepository;
        this.eventRepository = eventRepository;
        this.authorization = authorization;
    }

    @Transactional(readOnly = true)
    public ResourceAvailability get(long resourceId, Instant from, Instant to, long currentUserId) {
        ResourceEntity resource = resourceRepository.findById(resourceId).orElseThrow(NotFoundException::new);
        authorization.requireMember(resource.organizationId(), currentUserId);

        ResourceHealthStatus initialStatus = eventRepository
                .findFirstByResourceIdAndChangedAtLessThanEqualOrderByChangedAtDescIdDesc(resourceId, from)
                .map(ResourceHealthEventEntity::toStatus)
                .orElse(ResourceHealthStatus.UNKNOWN);
        List<ResourceHealthEventEntity> events = eventRepository
                .findAllByResourceIdAndChangedAtGreaterThanAndChangedAtLessThanOrderByChangedAtAscIdAsc(
                        resourceId, from, to
                );
        return calculate(from, to, initialStatus, events);
    }

    static ResourceAvailability calculate(
            Instant from,
            Instant to,
            ResourceHealthStatus initialStatus,
            List<ResourceHealthEventEntity> events
    ) {
        EnumMap<ResourceHealthStatus, Duration> durations = new EnumMap<>(ResourceHealthStatus.class);
        for (ResourceHealthStatus status : ResourceHealthStatus.values()) {
            durations.put(status, Duration.ZERO);
        }

        Instant intervalStart = from;
        ResourceHealthStatus status = initialStatus;
        for (ResourceHealthEventEntity event : events) {
            durations.merge(status, Duration.between(intervalStart, event.changedAt()), Duration::plus);
            intervalStart = event.changedAt();
            status = event.toStatus();
        }
        durations.merge(status, Duration.between(intervalStart, to), Duration::plus);

        long periodSeconds = Duration.between(from, to).getSeconds();
        EnumMap<ResourceHealthStatus, Long> seconds = roundedSeconds(durations, periodSeconds);
        long upSeconds = seconds.get(ResourceHealthStatus.UP);
        long degradedSeconds = seconds.get(ResourceHealthStatus.DEGRADED);
        long downSeconds = seconds.get(ResourceHealthStatus.DOWN);
        long unknownSeconds = seconds.get(ResourceHealthStatus.UNKNOWN);
        long knownSeconds = upSeconds + degradedSeconds + downSeconds;

        return new ResourceAvailability(
                from,
                to,
                periodSeconds,
                upSeconds,
                degradedSeconds,
                downSeconds,
                unknownSeconds,
                knownSeconds,
                percentage(upSeconds, knownSeconds),
                percentage(upSeconds + degradedSeconds, knownSeconds),
                percentage(knownSeconds, periodSeconds)
        );
    }

    private static EnumMap<ResourceHealthStatus, Long> roundedSeconds(
            EnumMap<ResourceHealthStatus, Duration> durations,
            long periodSeconds
    ) {
        EnumMap<ResourceHealthStatus, Long> seconds = new EnumMap<>(ResourceHealthStatus.class);
        long allocatedSeconds = 0;
        for (ResourceHealthStatus status : ResourceHealthStatus.values()) {
            long statusSeconds = durations.get(status).getSeconds();
            seconds.put(status, statusSeconds);
            allocatedSeconds += statusSeconds;
        }

        long remainder = periodSeconds - allocatedSeconds;
        List<ResourceHealthStatus> byLargestFraction = Arrays.stream(ResourceHealthStatus.values())
                .sorted(Comparator.comparingInt(
                        (ResourceHealthStatus status) -> durations.get(status).getNano()
                ).reversed())
                .toList();
        for (int index = 0; index < remainder; index++) {
            seconds.merge(byLargestFraction.get(index), 1L, Long::sum);
        }
        return seconds;
    }

    private static BigDecimal percentage(long value, long total) {
        if (total == 0) {
            return null;
        }
        return BigDecimal.valueOf(value)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total), PERCENT_SCALE, RoundingMode.HALF_UP);
    }
}
