package com.github.stimur1709.cloudops.resource.config;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import tools.jackson.databind.JsonNode;

@JsonSubTypes({
    @JsonSubTypes.Type(value = ServerResourceConfig.class, name = "SERVER"),
    @JsonSubTypes.Type(value = NetworkDeviceResourceConfig.class, name = "NETWORK_DEVICE"),
    @JsonSubTypes.Type(value = DatabaseResourceConfig.class, name = "DATABASE"),
    @JsonSubTypes.Type(value = ServiceResourceConfig.class, name = "SERVICE"),
    @JsonSubTypes.Type(value = OtherResourceConfig.class, name = "OTHER")
})
public sealed interface ResourceConfig
        permits ServerResourceConfig,
                NetworkDeviceResourceConfig,
                DatabaseResourceConfig,
                ServiceResourceConfig,
                OtherResourceConfig {

    @JsonAnySetter
    default void rejectUnknownField(String field, JsonNode value) {
        throw new UnknownResourceConfigFieldException(field);
    }
}
