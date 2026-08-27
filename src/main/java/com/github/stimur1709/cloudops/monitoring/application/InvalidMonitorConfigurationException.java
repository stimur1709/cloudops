package com.github.stimur1709.cloudops.monitoring.application;

public final class InvalidMonitorConfigurationException extends RuntimeException {

    private final String field;

    public InvalidMonitorConfigurationException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String field() {
        return field;
    }
}
