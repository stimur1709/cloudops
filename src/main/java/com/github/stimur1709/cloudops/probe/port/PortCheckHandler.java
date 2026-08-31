package com.github.stimur1709.cloudops.probe.port;

import com.github.stimur1709.cloudops.probe.ProbeType;
import com.github.stimur1709.cloudops.probe.execution.ProbeExecutionContext;
import com.github.stimur1709.cloudops.probe.execution.ProbeExecutionResult;
import com.github.stimur1709.cloudops.probe.execution.ProbeHandler;
import com.github.stimur1709.cloudops.resource.config.DatabaseResourceConfig;
import com.github.stimur1709.cloudops.resource.config.NetworkDeviceResourceConfig;
import com.github.stimur1709.cloudops.resource.config.ResourceConfig;
import com.github.stimur1709.cloudops.resource.config.ServerResourceConfig;
import org.springframework.stereotype.Component;

@Component
public class PortCheckHandler implements ProbeHandler {

    private final PortCheckClient client;

    public PortCheckHandler(PortCheckClient client) {
        this.client = client;
    }

    @Override
    public ProbeType type() {
        return ProbeType.PORT_CHECK;
    }

    @Override
    public boolean isCompatibleWith(ResourceConfig resourceConfig) {
        return switch (resourceConfig) {
            case ServerResourceConfig server -> server.port() != null;
            case NetworkDeviceResourceConfig device -> device.managementPort() != null;
            case DatabaseResourceConfig ignored -> true;
            default -> false;
        };
    }

    @Override
    public ProbeExecutionResult execute(ProbeExecutionContext context) {
        Endpoint endpoint = endpoint(context.resourceConfig());
        PortCheckOutcome outcome = client.execute(endpoint.host(), endpoint.port(), context.timeoutMs());
        if (outcome.completed()) {
            return ProbeExecutionResult.completed(true, outcome.result());
        }
        return ProbeExecutionResult.failed(outcome.errorCode(), outcome.errorMessage());
    }

    private Endpoint endpoint(ResourceConfig resourceConfig) {
        return switch (resourceConfig) {
            case ServerResourceConfig server when server.port() != null -> new Endpoint(server.host(), server.port());
            case NetworkDeviceResourceConfig device when device.managementPort() != null ->
                    new Endpoint(device.host(), device.managementPort());
            case DatabaseResourceConfig database -> new Endpoint(database.host(), database.port());
            default -> throw new IllegalArgumentException("PORT_CHECK requires resource configuration with host and port");
        };
    }

    private record Endpoint(String host, int port) {
    }
}
