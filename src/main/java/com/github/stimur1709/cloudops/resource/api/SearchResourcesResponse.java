package com.github.stimur1709.cloudops.resource.api;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.github.stimur1709.cloudops.resource.application.ResourceSearchResult;

public record SearchResourcesResponse(
        List<ResourceResponse> items,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        Long total
) {

    static SearchResourcesResponse from(ResourceSearchResult result) {
        return new SearchResourcesResponse(
                result.items().stream().map(ResourceResponse::from).toList(),
                result.total()
        );
    }
}
