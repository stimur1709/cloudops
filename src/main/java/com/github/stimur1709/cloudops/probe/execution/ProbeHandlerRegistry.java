package com.github.stimur1709.cloudops.probe.execution;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.github.stimur1709.cloudops.probe.ProbeType;
import com.github.stimur1709.cloudops.resource.config.ResourceConfig;
import org.springframework.stereotype.Component;

@Component
public class ProbeHandlerRegistry {

    private final Map<ProbeType, ProbeHandler> handlers;

    public ProbeHandlerRegistry(List<ProbeHandler> handlers) {
        Map<ProbeType, ProbeHandler> registered = new EnumMap<>(ProbeType.class);
        for (ProbeHandler handler : handlers) {
            ProbeHandler duplicate = registered.putIfAbsent(handler.supports(), handler);
            if (duplicate != null) {
                throw new IllegalStateException(
                        "Multiple ProbeHandler beans support probe type " + handler.supports()
                );
            }
        }
        this.handlers = Map.copyOf(registered);
    }

    public ProbeHandler get(ProbeType type) {
        ProbeHandler handler = handlers.get(type);
        if (handler == null) {
            throw new ProbeHandlerNotFoundException(type);
        }
        return handler;
    }

    public boolean supports(ProbeType type, ResourceConfig resourceConfig) {
        return get(type).supports(resourceConfig);
    }
}
