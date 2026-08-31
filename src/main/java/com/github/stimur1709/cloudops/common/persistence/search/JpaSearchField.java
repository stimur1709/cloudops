package com.github.stimur1709.cloudops.common.persistence.search;

import com.github.stimur1709.cloudops.common.search.SearchQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.EnumSet;
import java.util.Objects;
import java.util.function.Function;

public final class JpaSearchField<E, V> {

    private final Function<Root<E>, Expression<V>> expression;
    private final SearchValueConverter<V> converter;
    private final EnumSet<SearchQuery.Operation> operations;
    private final PredicateFactory<V> predicateFactory;
    private final boolean sortable;

    private JpaSearchField(
            Function<Root<E>, Expression<V>> expression,
            SearchValueConverter<V> converter,
            EnumSet<SearchQuery.Operation> operations,
            PredicateFactory<V> predicateFactory,
            boolean sortable) {
        this.expression = Objects.requireNonNull(expression);
        this.converter = Objects.requireNonNull(converter);
        this.operations = EnumSet.copyOf(operations);
        this.predicateFactory = Objects.requireNonNull(predicateFactory);
        this.sortable = sortable;
    }

    public static <E> JpaSearchField<E, String> text(Function<Root<E>, Expression<String>> expression) {
        return new JpaSearchField<>(
                expression,
                SearchValueConverter.stringValue(),
                EnumSet.of(SearchQuery.Operation.EQ, SearchQuery.Operation.NE, SearchQuery.Operation.CONTAINS),
                (builder, path, operation, value) -> switch (operation) {
                    case EQ -> builder.equal(path, value);
                    case NE -> builder.notEqual(path, value);
                    case CONTAINS -> builder.like(path, "%" + escapeLike(value) + "%", '\\');
                    default -> throw unsupportedOperation(operation);
                },
                false);
    }

    public static <E, V> JpaSearchField<E, V> equality(
            Function<Root<E>, Expression<V>> expression, SearchValueConverter<V> converter) {
        return new JpaSearchField<>(
                expression,
                converter,
                EnumSet.of(SearchQuery.Operation.EQ, SearchQuery.Operation.NE),
                (builder, path, operation, value) -> switch (operation) {
                    case EQ -> builder.equal(path, value);
                    case NE -> builder.notEqual(path, value);
                    default -> throw unsupportedOperation(operation);
                },
                false);
    }

    public static <E, V extends Comparable<? super V>> JpaSearchField<E, V> comparable(
            Function<Root<E>, Expression<V>> expression, SearchValueConverter<V> converter) {
        return new JpaSearchField<>(
                expression,
                converter,
                EnumSet.of(
                        SearchQuery.Operation.EQ,
                        SearchQuery.Operation.NE,
                        SearchQuery.Operation.GT,
                        SearchQuery.Operation.GE,
                        SearchQuery.Operation.LT,
                        SearchQuery.Operation.LE),
                (builder, path, operation, value) -> switch (operation) {
                    case EQ -> builder.equal(path, value);
                    case NE -> builder.notEqual(path, value);
                    case GT -> builder.greaterThan(path, value);
                    case GE -> builder.greaterThanOrEqualTo(path, value);
                    case LT -> builder.lessThan(path, value);
                    case LE -> builder.lessThanOrEqualTo(path, value);
                    case CONTAINS -> throw unsupportedOperation(operation);
                },
                false);
    }

    public JpaSearchField<E, V> allowing(SearchQuery.Operation first, SearchQuery.Operation... additional) {
        EnumSet<SearchQuery.Operation> allowed = EnumSet.of(first, additional);
        if (!operations.containsAll(allowed)) {
            throw new IllegalArgumentException("Operation is not supported by this field type");
        }
        return new JpaSearchField<>(expression, converter, allowed, predicateFactory, sortable);
    }

    public JpaSearchField<E, V> sortable() {
        return new JpaSearchField<>(expression, converter, operations, predicateFactory, true);
    }

    boolean supports(SearchQuery.Operation operation) {
        return operations.contains(operation);
    }

    boolean isSortable() {
        return sortable;
    }

    Object convert(String value, String fieldPath) {
        return converter.convert(value, fieldPath);
    }

    Predicate toPredicate(Root<E> root, CriteriaBuilder builder, SearchQuery.Operation operation, Object value) {
        return predicateFactory.create(builder, expression.apply(root), operation, cast(value));
    }

    Order toOrder(Root<E> root, CriteriaBuilder builder, SearchQuery.Direction direction) {
        Expression<V> path = expression.apply(root);
        return direction == SearchQuery.Direction.ASC ? builder.asc(path) : builder.desc(path);
    }

    @SuppressWarnings("unchecked")
    private V cast(Object value) {
        return (V) value;
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static IllegalStateException unsupportedOperation(SearchQuery.Operation operation) {
        return new IllegalStateException("Operation was validated before query creation: " + operation);
    }

    @FunctionalInterface
    private interface PredicateFactory<V> {

        Predicate create(CriteriaBuilder builder, Expression<V> expression, SearchQuery.Operation operation, V value);
    }
}
