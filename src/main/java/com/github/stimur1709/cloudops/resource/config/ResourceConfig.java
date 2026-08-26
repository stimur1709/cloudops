package com.github.stimur1709.cloudops.resource.config;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonAnySetter;

@JsonSubTypes({
        @JsonSubTypes.Type(value = ServerResourceConfig.class, name = "SERVER"),
        @JsonSubTypes.Type(value = NetworkDeviceResourceConfig.class, name = "NETWORK_DEVICE"),
        @JsonSubTypes.Type(value = DatabaseResourceConfig.class, name = "DATABASE"),
        @JsonSubTypes.Type(value = ServiceResourceConfig.class, name = "SERVICE"),
        @JsonSubTypes.Type(value = OtherResourceConfig.class, name = "OTHER")
})
public sealed interface ResourceConfig permits ServerResourceConfig, NetworkDeviceResourceConfig,
        DatabaseResourceConfig, ServiceResourceConfig, OtherResourceConfig {

    @JsonAnySetter
    default void rejectUnknownField(String field) {
        throw new UnknownResourceConfigFieldException(field);
    }
}
