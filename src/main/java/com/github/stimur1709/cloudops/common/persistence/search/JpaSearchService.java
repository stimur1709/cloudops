package com.github.stimur1709.cloudops.common.persistence.search;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import com.github.stimur1709.cloudops.common.search.InvalidSearchException;
import com.github.stimur1709.cloudops.common.search.SearchQuery;
import com.github.stimur1709.cloudops.common.search.SearchResult;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.stereotype.Service;

@Service
public class JpaSearchService {

    private final EntityManager entityManager;

    public JpaSearchService(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public <E> SearchResult<E> search(SearchQuery search, JpaSearchDefinition<E> definition) {
        PreparedSearch<E> preparedSearch = prepare(search, definition);
        CriteriaBuilder builder = entityManager.getCriteriaBuilder();

        CriteriaQuery<E> criteria = builder.createQuery(definition.entityType());
        Root<E> root = criteria.from(definition.entityType());
        criteria.select(root);
        applyFilter(criteria, root, builder, preparedSearch.filter());
        criteria.orderBy(toOrders(root, builder, preparedSearch.sort(), definition));

        TypedQuery<E> query = entityManager.createQuery(criteria)
                .setFirstResult(search.start())
                .setMaxResults(search.size());
        List<E> items = query.getResultList();

        Long total = search.getTotal() ? count(builder, preparedSearch.filter(), definition) : null;
        return new SearchResult<>(items, total);
    }

    private <E> Long count(
            CriteriaBuilder builder,
            PreparedFilter<E> filter,
            JpaSearchDefinition<E> definition
    ) {
        CriteriaQuery<Long> criteria = builder.createQuery(Long.class);
        Root<E> root = criteria.from(definition.entityType());
        criteria.select(builder.count(root));
        applyFilter(criteria, root, builder, filter);
        return entityManager.createQuery(criteria).getSingleResult();
    }

    private <E> PreparedSearch<E> prepare(SearchQuery search, JpaSearchDefinition<E> definition) {
        PreparedFilter<E> filter = prepareFilter(search.filter(), definition);
        List<PreparedSort<E>> sort = prepareSort(search.sort(), definition);
        return new PreparedSearch<>(filter, sort);
    }

    private <E> PreparedFilter<E> prepareFilter(
            SearchQuery.Filter filter,
            JpaSearchDefinition<E> definition
    ) {
        if (filter == null) {
            return null;
        }

        List<PreparedCondition<E>> conditions = IntStream
                .range(0, filter.conditions().size())
                .mapToObj(index -> prepareCondition(filter.conditions().get(index), index, definition))
                .toList();
        return new PreparedFilter<>(filter.operator(), conditions);
    }

    private <E> PreparedCondition<E> prepareCondition(
            SearchQuery.Condition condition,
            int index,
            JpaSearchDefinition<E> definition
    ) {
        String path = "filter.conditions[%d]".formatted(index);
        JpaSearchField<E, ?> field = definition.fields().get(condition.field());
        if (field == null) {
            throw invalid(path + ".field", "Unknown filter field: " + condition.field());
        }
        if (!field.supports(condition.operation())) {
            throw invalid(
                    path + ".operation",
                    "Operation %s is not supported for field %s"
                            .formatted(condition.operation(), condition.field())
            );
        }

        Object value = field.convert(condition.value(), path + ".value");
        return new PreparedCondition<>(field, condition.operation(), value);
    }

    private <E> List<PreparedSort<E>> prepareSort(
            List<SearchQuery.Sort> sort,
            JpaSearchDefinition<E> definition
    ) {
        List<PreparedSort<E>> prepared = new ArrayList<>(IntStream.range(0, sort.size())
                .mapToObj(index -> {
                    SearchQuery.Sort item = sort.get(index);
                    JpaSearchField<E, ?> field = definition.fields().get(item.field());
                    if (field == null || !field.isSortable()) {
                        throw invalid("sort[%d].field".formatted(index), "Unknown sort field: " + item.field());
                    }
                    return new PreparedSort<>(item.field(), field, item.order());
                })
                .toList());
        boolean hasDefaultSort = prepared.stream()
                .anyMatch(item -> item.name().equals(definition.defaultSortField()));
        if (!prepared.isEmpty() && !hasDefaultSort) {
            prepared.add(new PreparedSort<>(
                    definition.defaultSortField(),
                    definition.fields().get(definition.defaultSortField()),
                    SearchQuery.Direction.ASC
            ));
        }
        return List.copyOf(prepared);
    }

    private <E> void applyFilter(
            CriteriaQuery<?> criteria,
            Root<E> root,
            CriteriaBuilder builder,
            PreparedFilter<E> filter
    ) {
        if (filter == null) {
            return;
        }

        Predicate[] predicates = filter.conditions().stream()
                .map(condition -> condition.field().toPredicate(
                        root,
                        builder,
                        condition.operation(),
                        condition.value()
                ))
                .toArray(Predicate[]::new);
        Predicate predicate = filter.operator() == SearchQuery.LogicalOperator.AND
                ? builder.and(predicates)
                : builder.or(predicates);
        criteria.where(predicate);
    }

    private <E> List<Order> toOrders(
            Root<E> root,
            CriteriaBuilder builder,
            List<PreparedSort<E>> sort,
            JpaSearchDefinition<E> definition
    ) {
        if (sort.isEmpty()) {
            JpaSearchField<E, ?> field = definition.fields().get(definition.defaultSortField());
            return List.of(field.toOrder(root, builder, SearchQuery.Direction.ASC));
        }
        return sort.stream()
                .map(item -> item.field().toOrder(root, builder, item.order()))
                .toList();
    }

    private InvalidSearchException invalid(String field, String message) {
        return new InvalidSearchException(field, message);
    }

    private record PreparedSearch<E>(PreparedFilter<E> filter, List<PreparedSort<E>> sort) {
    }

    private record PreparedFilter<E>(
            SearchQuery.LogicalOperator operator,
            List<PreparedCondition<E>> conditions
    ) {
    }

    private record PreparedCondition<E>(
            JpaSearchField<E, ?> field,
            SearchQuery.Operation operation,
            Object value
    ) {
    }

    private record PreparedSort<E>(
            String name,
            JpaSearchField<E, ?> field,
            SearchQuery.Direction order
    ) {
    }
}
