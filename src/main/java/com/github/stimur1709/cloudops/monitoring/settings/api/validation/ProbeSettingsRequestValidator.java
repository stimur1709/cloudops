package com.github.stimur1709.cloudops.monitoring.settings.api.validation;

import com.github.stimur1709.cloudops.monitoring.StorageMode;
import com.github.stimur1709.cloudops.monitoring.config.MonitoringProperties;
import com.github.stimur1709.cloudops.monitoring.settings.api.ProbeSettingsRequest;
import com.github.stimur1709.cloudops.probe.ProbeType;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

public class ProbeSettingsRequestValidator implements ConstraintValidator<ValidProbeSettings, ProbeSettingsRequest> {

    private final MonitoringProperties properties;

    public ProbeSettingsRequestValidator(MonitoringProperties properties) {
        this.properties = properties;
    }

    @Override
    public boolean isValid(ProbeSettingsRequest value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        boolean valid = true;
        context.disableDefaultConstraintViolation();

        if (value.intervalSeconds() != null && value.intervalSeconds() > 0
                && value.intervalSeconds() < properties.minimumIntervalSeconds()) {
            violation(
                    context,
                    "intervalSeconds",
                    "Interval must be at least " + properties.minimumIntervalSeconds() + " seconds"
            );
            valid = false;
        }

        boolean invalidHistoryRetention = value.retentionDays() == null
                || value.retentionDays() < 1
                || value.retentionDays() > 365;
        if (value.storageMode() == StorageMode.HISTORY && invalidHistoryRetention) {
            violation(context, "retentionDays", "Retention days must be between 1 and 365 for HISTORY");
            valid = false;
        } else if (value.storageMode() == StorageMode.LATEST_ONLY && value.retentionDays() != null) {
            violation(context, "retentionDays", "Retention days must be omitted for LATEST_ONLY");
            valid = false;
        }

        ProbeType type = pathProbeType();
        if (type == ProbeType.DNS_CHECK && value.timeoutMs() != null) {
            violation(context, "timeoutMs", "Timeout is not supported for DNS_CHECK");
            valid = false;
        } else if (type != null && type != ProbeType.DNS_CHECK && value.timeoutMs() == null) {
            violation(context, "timeoutMs", "Timeout is required");
            valid = false;
        } else if (value.timeoutMs() != null
                && (value.timeoutMs() < 1 || value.timeoutMs() > 60000)) {
            violation(context, "timeoutMs", "Timeout must be between 1 and 60000 milliseconds");
            valid = false;
        }
        return valid;
    }

    private ProbeType pathProbeType() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes servlet)) {
            return null;
        }
        String value = servlet.getRequest().getRequestURI();
        int slash = value.lastIndexOf('/');
        try {
            return ProbeType.valueOf(value.substring(slash + 1));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private void violation(ConstraintValidatorContext context, String field, String message) {
        context.buildConstraintViolationWithTemplate(message).addPropertyNode(field).addConstraintViolation();
    }
}
