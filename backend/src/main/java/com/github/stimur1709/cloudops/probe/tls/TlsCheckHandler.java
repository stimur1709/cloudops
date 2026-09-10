package com.github.stimur1709.cloudops.probe.tls;

import com.github.stimur1709.cloudops.probe.ProbeType;
import com.github.stimur1709.cloudops.probe.execution.ProbeExecutionContext;
import com.github.stimur1709.cloudops.probe.execution.ProbeExecutionResult;
import com.github.stimur1709.cloudops.probe.execution.ProbeHandler;
import com.github.stimur1709.cloudops.resource.config.DatabaseResourceConfig;
import com.github.stimur1709.cloudops.resource.config.NetworkDeviceResourceConfig;
import com.github.stimur1709.cloudops.resource.config.ResourceConfig;
import com.github.stimur1709.cloudops.resource.config.ServerResourceConfig;
import com.github.stimur1709.cloudops.resource.config.ServiceResourceConfig;
import java.net.URI;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class TlsCheckHandler implements ProbeHandler {

    private final TlsCheckClient client;

    public TlsCheckHandler(TlsCheckClient client) {
        this.client = client;
    }

    @Override
    public ProbeType type() {
        return ProbeType.TLS_CHECK;
    }

    @Override
    public boolean isCompatibleWith(ResourceConfig resourceConfig) {
        return endpoint(resourceConfig) != null;
    }

    @Override
    public ProbeExecutionResult execute(ProbeExecutionContext context) {
        Endpoint endpoint = endpoint(context.resourceConfig());
        if (endpoint == null) {
            throw new IllegalArgumentException("TLS_CHECK requires a resource configuration with a TLS host and port");
        }
        TlsCheckOutcome outcome = client.execute(endpoint.host(), endpoint.port(), context.timeoutMs());
        if (outcome.completed()) {
            return ProbeExecutionResult.completed(true, outcome.result());
        }
        return ProbeExecutionResult.failed(outcome.errorCode(), outcome.errorMessage());
    }

    private Endpoint endpoint(ResourceConfig resourceConfig) {
        return switch (resourceConfig) {
            case ServerResourceConfig server when server.port() != null -> new Endpoint(server.host(), server.port());
            case NetworkDeviceResourceConfig device
            when device.managementPort() != null -> new Endpoint(device.host(), device.managementPort());
            case DatabaseResourceConfig database -> new Endpoint(database.host(), database.port());
            case ServiceResourceConfig service -> serviceEndpoint(service);
            default -> null;
        };
    }

    private Endpoint serviceEndpoint(ServiceResourceConfig service) {
        URI uri = URI.create(service.url());
        if (!"https".equals(uri.getScheme().toLowerCase(Locale.ROOT))) {
            return null;
        }
        return new Endpoint(uri.getHost(), uri.getPort() == -1 ? 443 : uri.getPort());
    }

    private record Endpoint(String host, int port) {}
}
