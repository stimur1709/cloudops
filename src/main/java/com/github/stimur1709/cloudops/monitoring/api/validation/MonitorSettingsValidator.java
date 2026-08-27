package com.github.stimur1709.cloudops.monitoring.api.validation;

import com.github.stimur1709.cloudops.monitoring.StorageMode;
import com.github.stimur1709.cloudops.monitoring.config.MonitoringProperties;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public final class MonitorSettingsValidator implements ConstraintValidator<ValidMonitorSettings, MonitorSettings> {

    private final MonitoringProperties properties;

    public MonitorSettingsValidator(MonitoringProperties properties) {
        this.properties = properties;
    }

    @Override
    public boolean isValid(MonitorSettings settings, ConstraintValidatorContext context) {
        if (settings == null) {
            return true;
        }

        context.disableDefaultConstraintViolation();
        boolean valid = true;
        Integer intervalSeconds = settings.intervalSeconds();
        if (intervalSeconds != null
                && intervalSeconds > 0
                && intervalSeconds < properties.minimumIntervalSeconds()) {
            addViolation(
                    context,
                    "intervalSeconds",
                    "Interval must be at least %d seconds".formatted(properties.minimumIntervalSeconds())
            );
            valid = false;
        }

        StorageMode storageMode = settings.storageMode();
        Integer retentionDays = settings.retentionDays();
        if (storageMode == StorageMode.HISTORY
                && (retentionDays == null || retentionDays < 1 || retentionDays > 365)) {
            addViolation(context, "retentionDays", "Retention days must be between 1 and 365 for HISTORY");
            valid = false;
        } else if (storageMode == StorageMode.LATEST_ONLY && retentionDays != null) {
            addViolation(context, "retentionDays", "Retention days must be null for LATEST_ONLY");
            valid = false;
        }
        return valid;
    }

    private void addViolation(ConstraintValidatorContext context, String field, String message) {
        context.buildConstraintViolationWithTemplate(message)
                .addPropertyNode(field)
                .addConstraintViolation();
    }
}
