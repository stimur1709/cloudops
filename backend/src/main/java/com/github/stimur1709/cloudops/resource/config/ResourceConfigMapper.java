package com.github.stimur1709.cloudops.resource.config;

import com.github.stimur1709.cloudops.resource.ResourceType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class ResourceConfigMapper {

    private final ObjectMapper objectMapper;

    public ResourceConfigMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public JsonNode toJson(ResourceConfig config) {
        return objectMapper.valueToTree(config);
    }

    public ResourceConfig fromJson(ResourceType type, JsonNode config) {
        return objectMapper.treeToValue(config, configClass(type));
    }

    private Class<? extends ResourceConfig> configClass(ResourceType type) {
        return switch (type) {
            case SERVER -> ServerResourceConfig.class;
            case NETWORK_DEVICE -> NetworkDeviceResourceConfig.class;
            case DATABASE -> DatabaseResourceConfig.class;
            case SERVICE -> ServiceResourceConfig.class;
            case OTHER -> OtherResourceConfig.class;
        };
    }
}
