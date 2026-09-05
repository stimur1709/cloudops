package com.github.stimur1709.cloudops.monitoring.settings.application;

import com.github.stimur1709.cloudops.probe.ProbeType;

record PendingSettings(boolean organization, long id, ProbeType type) {}
