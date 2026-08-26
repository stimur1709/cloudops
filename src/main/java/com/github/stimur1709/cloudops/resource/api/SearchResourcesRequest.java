package com.github.stimur1709.cloudops.resource.api;

import java.util.List;

import com.github.stimur1709.cloudops.resource.application.ResourceSearch;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public record SearchResourcesRequest(
        @NotNull(message = "Start is required")
        @Min(value = 0, message = "Start must not be less than 0")
        Integer start,

        @NotNull(message = "Size is required")
        @Min(value = 1, message = "Size must be greater than 0")
        @Max(value = 100, message = "Size must not be greater than 100")
        Integer size,

        @Valid
        Filter filter,

        List<@Valid Sort> sort,

        boolean getTotal
) {

    ResourceSearch toSearch() {
        ResourceSearch.Filter searchFilter = filter == null ? null : filter.toSearch();
        List<ResourceSearch.Sort> searchSort = sort == null
                ? List.of()
                : sort.stream().map(Sort::toSearch).toList();
        return new ResourceSearch(start, size, searchFilter, searchSort, getTotal);
    }

    public record Filter(
            @NotNull(message = "Filter operator is required")
            ResourceSearch.LogicalOperator operator,

            @NotEmpty(message = "Filter conditions must not be empty")
            List<@Valid Condition> conditions
    ) {

        ResourceSearch.Filter toSearch() {
            return new ResourceSearch.Filter(
                    operator,
                    conditions.stream().map(Condition::toSearch).toList()
            );
        }
    }

    public record Condition(
            @NotBlank(message = "Filter field must not be blank")
            String field,

            @NotNull(message = "Filter operation is required")
            ResourceSearch.Operation operation,

            @NotNull(message = "Filter value is required")
            String value
    ) {

        ResourceSearch.Condition toSearch() {
            return new ResourceSearch.Condition(field, operation, value);
        }
    }

    public record Sort(
            @NotBlank(message = "Sort field must not be blank")
            String field,

            @NotNull(message = "Sort order is required")
            ResourceSearch.Direction order
    ) {

        ResourceSearch.Sort toSearch() {
            return new ResourceSearch.Sort(field, order);
        }
    }
}
