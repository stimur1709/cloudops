package com.github.stimur1709.cloudops.common.api.search;

import java.util.List;
import java.util.function.Function;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.github.stimur1709.cloudops.common.search.SearchResult;

public record SearchResponse<T>(
        List<T> items,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        Long total
) {

    public SearchResponse {
        items = List.copyOf(items);
    }

    public static <S, T> SearchResponse<T> from(
            SearchResult<S> result,
            Function<? super S, T> mapper
    ) {
        return new SearchResponse<>(
                result.items().stream().map(mapper).toList(),
                result.total()
        );
    }
}
