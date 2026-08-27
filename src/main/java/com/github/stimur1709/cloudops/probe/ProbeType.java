package com.github.stimur1709.cloudops.probe;

import com.github.stimur1709.cloudops.resource.ResourceType;

public enum ProbeType {
    HTTP_CHECK(ResourceType.SERVICE);

    private final ResourceType resourceType;

    ProbeType(ResourceType resourceType) {
        this.resourceType = resourceType;
    }

    public boolean supports(ResourceType resourceType) {
        return this.resourceType == resourceType;
    }
}
