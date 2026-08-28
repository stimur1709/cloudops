package com.github.stimur1709.cloudops.probe;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.stimur1709.cloudops.resource.config.DatabaseResourceConfig;
import com.github.stimur1709.cloudops.resource.config.NetworkDeviceResourceConfig;
import com.github.stimur1709.cloudops.resource.config.OtherResourceConfig;
import com.github.stimur1709.cloudops.resource.config.ServerResourceConfig;
import com.github.stimur1709.cloudops.resource.config.ServiceResourceConfig;
import org.junit.jupiter.api.Test;

class ResourceHostExtractorTest {

    @Test
    void extractsHostFromSupportedResourceConfigurations() {
        assertThat(ResourceHostExtractor.extract(new ServerResourceConfig("server.local", null)))
                .isEqualTo("server.local");
        assertThat(ResourceHostExtractor.extract(new NetworkDeviceResourceConfig("switch.local", null)))
                .isEqualTo("switch.local");
        assertThat(ResourceHostExtractor.extract(new DatabaseResourceConfig("database.local", 5432, "cloudops")))
                .isEqualTo("database.local");
        assertThat(ResourceHostExtractor.extract(
                new ServiceResourceConfig("https://api.local:8443/status", 200, 1000)
        )).isEqualTo("api.local");
    }

    @Test
    void returnsNullForResourceWithoutHost() {
        assertThat(ResourceHostExtractor.extract(new OtherResourceConfig())).isNull();
    }
}
