package com.github.stimur1709.cloudops.monitoring.api.validation;

import com.github.stimur1709.cloudops.monitoring.StorageMode;

public interface MonitorSettings {

    Integer intervalSeconds();

    StorageMode storageMode();

    Integer retentionDays();
}
