package com.github.stimur1709.cloudops.probe;

import java.net.URI;

import com.github.stimur1709.cloudops.resource.config.DatabaseResourceConfig;
import com.github.stimur1709.cloudops.resource.config.NetworkDeviceResourceConfig;
import com.github.stimur1709.cloudops.resource.config.ResourceConfig;
import com.github.stimur1709.cloudops.resource.config.ServerResourceConfig;
import com.github.stimur1709.cloudops.resource.config.ServiceResourceConfig;

public final class ResourceHostExtractor {

    private ResourceHostExtractor() {
    }

    public static String extract(ResourceConfig resourceConfig) {
        return switch (resourceConfig) {
            case ServerResourceConfig server -> server.host();
            case NetworkDeviceResourceConfig device -> device.host();
            case DatabaseResourceConfig database -> database.host();
            case ServiceResourceConfig service -> URI.create(service.url()).getHost();
            default -> null;
        };
    }
}
