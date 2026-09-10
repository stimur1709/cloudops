package com.github.stimur1709.cloudops.monitoring.execution;

import com.github.stimur1709.cloudops.probe.ProbeType;
import java.time.Instant;

record ClaimedMonitor(long id, long resourceId, long organizationId, ProbeType type, Instant nextRunAt) {}
