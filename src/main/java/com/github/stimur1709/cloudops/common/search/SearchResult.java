package com.github.stimur1709.cloudops.common.search;

import java.util.List;

public record SearchResult<T>(List<T> items, Long total) {

    public SearchResult {
        items = List.copyOf(items);
    }
}
