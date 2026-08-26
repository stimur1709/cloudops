package com.github.stimur1709.cloudops.resource.application;

import java.util.List;

import com.github.stimur1709.cloudops.resource.persistence.ResourceEntity;

public record ResourceSearchResult(List<ResourceEntity> items, Long total) {

    public ResourceSearchResult {
        items = List.copyOf(items);
    }
}
