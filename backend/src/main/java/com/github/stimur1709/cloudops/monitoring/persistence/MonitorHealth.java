package com.github.stimur1709.cloudops.monitoring.persistence;

import com.github.stimur1709.cloudops.monitoring.HealthStatus;
import com.github.stimur1709.cloudops.probe.ProbeType;

public interface MonitorHealth {
    ProbeType getType();

    boolean isCompatible();

    HealthStatus getHealthStatus();
}
