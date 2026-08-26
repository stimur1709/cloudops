package com.github.stimur1709.cloudops.resource.application;

import java.util.List;

public record ResourceSearch(
        int start,
        int size,
        Filter filter,
        List<Sort> sort,
        boolean getTotal
) {

    public ResourceSearch {
        sort = sort == null ? List.of() : List.copyOf(sort);
    }

    public record Filter(LogicalOperator operator, List<Condition> conditions) {

        public Filter {
            conditions = conditions == null ? List.of() : List.copyOf(conditions);
        }
    }

    public record Condition(String field, Operation operation, String value) {
    }

    public record Sort(String field, Direction order) {
    }

    public enum LogicalOperator {
        AND,
        OR
    }

    public enum Operation {
        EQ,
        NE,
        CONTAINS,
        GT,
        GE,
        LT,
        LE
    }

    public enum Direction {
        ASC,
        DESC
    }
}
