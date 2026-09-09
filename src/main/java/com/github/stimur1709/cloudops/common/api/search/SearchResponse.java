package com.github.stimur1709.cloudops.common.api.search;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.github.stimur1709.cloudops.common.search.SearchResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.function.Function;

public record SearchResponse<T>(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<T> items,

        @JsonInclude(JsonInclude.Include.NON_NULL)
        @Schema(
                description = "Matching count, present only when getTotal=true",
                requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        Long total) {

    public SearchResponse {
        items = List.copyOf(items);
    }

    public static <S, T> SearchResponse<T> from(SearchResult<S> result, Function<? super S, T> mapper) {
        return new SearchResponse<>(result.items().stream().map(mapper).toList(), result.total());
    }
}
