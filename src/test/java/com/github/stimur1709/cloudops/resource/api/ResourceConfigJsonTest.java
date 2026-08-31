package com.github.stimur1709.cloudops.resource.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.stimur1709.cloudops.resource.config.ServerResourceConfig;
import com.github.stimur1709.cloudops.resource.config.ServiceResourceConfig;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class ResourceConfigJsonTest {

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Test
    void usesExternalResourceTypeToDeserializeConfig() {
        CreateResourceRequest request = jsonMapper.readValue("""
                {
                  "name": "server",
                  "type": "SERVER",
                  "status": "ACTIVE",
                  "organizationId": 1,
                  "config": {"host": "server.internal", "port": 22}
                }
                """, CreateResourceRequest.class);

        assertThat(request.config()).isEqualTo(new ServerResourceConfig("server.internal", 22));
    }

    @Test
    void appliesServiceDefaultsDuringDeserialization() {
        CreateResourceRequest request = jsonMapper.readValue("""
                {
                  "name": "service",
                  "type": "SERVICE",
                  "status": "ACTIVE",
                  "organizationId": 1,
                  "config": {"url": "https://api.example.com"}
                }
                """, CreateResourceRequest.class);

        assertThat(request.config()).isEqualTo(new ServiceResourceConfig("https://api.example.com", 200));
    }

    @Test
    void rejectsUnknownAndTypeIncompatibleConfigFields() {
        assertThatThrownBy(() -> jsonMapper.readValue("""
                {
                  "name": "server",
                  "type": "SERVER",
                  "status": "ACTIVE",
                  "organizationId": 1,
                  "config": {"url": "https://api.example.com"}
                }
                """, CreateResourceRequest.class))
                .hasMessageContaining("url");
    }
}
