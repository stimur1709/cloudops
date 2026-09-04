package com.github.stimur1709.cloudops.ssh;

import com.github.stimur1709.cloudops.resource.config.NetworkDeviceResourceConfig;
import com.github.stimur1709.cloudops.resource.config.ResourceConfig;
import com.github.stimur1709.cloudops.resource.config.ServerResourceConfig;

public final class SshEndpointResolver {

    private static final int DEFAULT_SSH_PORT = 22;

    private SshEndpointResolver() {}

    public static boolean supports(ResourceConfig resourceConfig) {
        return resourceConfig instanceof ServerResourceConfig || resourceConfig instanceof NetworkDeviceResourceConfig;
    }

    public static SshEndpoint resolve(ResourceConfig resourceConfig) {
        return switch (resourceConfig) {
            case ServerResourceConfig server -> new SshEndpoint(server.host(), server.sshPort());
            case NetworkDeviceResourceConfig device ->
                new SshEndpoint(
                        device.host(), device.managementPort() == null ? DEFAULT_SSH_PORT : device.managementPort());
            default -> throw new IllegalArgumentException("SSH requires a server or network device");
        };
    }
}
