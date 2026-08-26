package com.github.stimur1709.cloudops.common.api.search;

import java.util.List;

import com.github.stimur1709.cloudops.common.search.SearchQuery;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public record SearchRequest(
        @NotNull(message = "Start is required")
        @Min(value = 0, message = "Start must not be less than 0")
        Integer start,

        @NotNull(message = "Size is required")
        @Min(value = 1, message = "Size must be greater than 0")
        @Max(value = 100, message = "Size must not be greater than 100")
        Integer size,

        @Valid
        Filter filter,

        List<@NotNull(message = "Sort item must not be null") @Valid Sort> sort,

        boolean getTotal
) {

    public SearchQuery toQuery() {
        SearchQuery.Filter searchFilter = filter == null ? null : filter.toQuery();
        List<SearchQuery.Sort> searchSort = sort == null
                ? List.of()
                : sort.stream().map(Sort::toQuery).toList();
        return new SearchQuery(start, size, searchFilter, searchSort, getTotal);
    }

    public record Filter(
            @NotNull(message = "Filter operator is required")
            SearchQuery.LogicalOperator operator,

            @NotEmpty(message = "Filter conditions must not be empty")
            List<@NotNull(message = "Filter condition must not be null") @Valid Condition> conditions
    ) {

        SearchQuery.Filter toQuery() {
            return new SearchQuery.Filter(
                    operator,
                    conditions.stream().map(Condition::toQuery).toList()
            );
        }
    }

    public record Condition(
            @NotBlank(message = "Filter field must not be blank")
            String field,

            @NotNull(message = "Filter operation is required")
            SearchQuery.Operation operation,

            @NotNull(message = "Filter value is required")
            String value
    ) {

        SearchQuery.Condition toQuery() {
            return new SearchQuery.Condition(field, operation, value);
        }
    }

    public record Sort(
            @NotBlank(message = "Sort field must not be blank")
            String field,

            @NotNull(message = "Sort order is required")
            SearchQuery.Direction order
    ) {

        SearchQuery.Sort toQuery() {
            return new SearchQuery.Sort(field, order);
        }
    }
}
