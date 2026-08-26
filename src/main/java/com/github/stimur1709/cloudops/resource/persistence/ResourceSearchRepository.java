package com.github.stimur1709.cloudops.resource.persistence;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.EnumSet;
import java.util.List;
import java.util.stream.IntStream;

import com.github.stimur1709.cloudops.resource.ResourceStatus;
import com.github.stimur1709.cloudops.resource.ResourceType;
import com.github.stimur1709.cloudops.resource.application.InvalidResourceSearchException;
import com.github.stimur1709.cloudops.resource.application.ResourceSearch;
import com.github.stimur1709.cloudops.resource.application.ResourceSearchResult;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.stereotype.Repository;

@Repository
public class ResourceSearchRepository {

    private static final EnumSet<ResourceSearch.Operation> EQUALITY_OPERATIONS = EnumSet.of(
            ResourceSearch.Operation.EQ,
            ResourceSearch.Operation.NE
    );
    private static final EnumSet<ResourceSearch.Operation> COMPARISON_OPERATIONS = EnumSet.of(
            ResourceSearch.Operation.EQ,
            ResourceSearch.Operation.NE,
            ResourceSearch.Operation.GT,
            ResourceSearch.Operation.GE,
            ResourceSearch.Operation.LT,
            ResourceSearch.Operation.LE
    );
    private static final EnumSet<ResourceSearch.Operation> STRING_OPERATIONS = EnumSet.of(
            ResourceSearch.Operation.EQ,
            ResourceSearch.Operation.NE,
            ResourceSearch.Operation.CONTAINS
    );

    private final EntityManager entityManager;

    public ResourceSearchRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public ResourceSearchResult search(ResourceSearch search) {
        PreparedSearch preparedSearch = prepare(search);
        CriteriaBuilder builder = entityManager.getCriteriaBuilder();

        CriteriaQuery<ResourceEntity> criteria = builder.createQuery(ResourceEntity.class);
        Root<ResourceEntity> root = criteria.from(ResourceEntity.class);
        criteria.select(root);
        applyFilter(criteria, root, builder, preparedSearch.filter());
        criteria.orderBy(toOrders(root, builder, preparedSearch.sort()));

        TypedQuery<ResourceEntity> query = entityManager.createQuery(criteria)
                .setFirstResult(search.start())
                .setMaxResults(search.size());
        List<ResourceEntity> items = query.getResultList();

        Long total = search.getTotal() ? count(builder, preparedSearch.filter()) : null;
        return new ResourceSearchResult(items, total);
    }

    private Long count(CriteriaBuilder builder, PreparedFilter filter) {
        CriteriaQuery<Long> criteria = builder.createQuery(Long.class);
        Root<ResourceEntity> root = criteria.from(ResourceEntity.class);
        criteria.select(builder.count(root));
        applyFilter(criteria, root, builder, filter);
        return entityManager.createQuery(criteria).getSingleResult();
    }

    private PreparedSearch prepare(ResourceSearch search) {
        PreparedFilter filter = prepareFilter(search.filter());
        List<PreparedSort> sort = prepareSort(search.sort());
        return new PreparedSearch(filter, sort);
    }

    private PreparedFilter prepareFilter(ResourceSearch.Filter filter) {
        if (filter == null) {
            return null;
        }

        List<PreparedCondition> conditions = IntStream
                .range(0, filter.conditions().size())
                .mapToObj(index -> prepareCondition(filter.conditions().get(index), index))
                .toList();
        return new PreparedFilter(filter.operator(), conditions);
    }

    private PreparedCondition prepareCondition(ResourceSearch.Condition condition, int index) {
        String path = "filter.conditions[%d]".formatted(index);
        SearchField field = SearchField.from(condition.field());
        if (field == null) {
            throw invalid(path + ".field", "Unknown filter field: " + condition.field());
        }
        if (!field.operations().contains(condition.operation())) {
            throw invalid(
                    path + ".operation",
                    "Operation %s is not supported for field %s"
                            .formatted(condition.operation(), condition.field())
            );
        }

        return new PreparedCondition(field, condition.operation(), convert(condition.value(), field, path));
    }

    private Object convert(String value, SearchField field, String path) {
        try {
            return switch (field) {
                case ID -> Long.valueOf(value);
                case NAME -> value;
                case TYPE -> ResourceType.valueOf(value);
                case STATUS -> ResourceStatus.valueOf(value);
                case CREATED_AT, UPDATED_AT -> Instant.parse(value);
            };
        } catch (NumberFormatException exception) {
            throw invalid(path + ".value", "Value must be a valid integer number");
        } catch (DateTimeParseException exception) {
            throw invalid(path + ".value", "Value must be a valid ISO-8601 instant");
        } catch (IllegalArgumentException exception) {
            String allowedValues = field == SearchField.TYPE
                    ? "NETWORK_DEVICE, SERVER, DATABASE, OTHER"
                    : "ACTIVE, INACTIVE";
            throw invalid(path + ".value", "Value must be one of: " + allowedValues);
        }
    }

    private List<PreparedSort> prepareSort(List<ResourceSearch.Sort> sort) {
        return IntStream.range(0, sort.size())
                .mapToObj(index -> {
                    ResourceSearch.Sort item = sort.get(index);
                    SearchField field = SearchField.from(item.field());
                    if (field == null) {
                        throw invalid("sort[%d].field".formatted(index), "Unknown sort field: " + item.field());
                    }
                    return new PreparedSort(field, item.order());
                })
                .toList();
    }

    private void applyFilter(
            CriteriaQuery<?> criteria,
            Root<ResourceEntity> root,
            CriteriaBuilder builder,
            PreparedFilter filter
    ) {
        if (filter == null) {
            return;
        }

        Predicate[] predicates = filter.conditions().stream()
                .map(condition -> toPredicate(root, builder, condition))
                .toArray(Predicate[]::new);
        Predicate predicate = filter.operator() == ResourceSearch.LogicalOperator.AND
                ? builder.and(predicates)
                : builder.or(predicates);
        criteria.where(predicate);
    }

    private Predicate toPredicate(
            Root<ResourceEntity> root,
            CriteriaBuilder builder,
            PreparedCondition condition
    ) {
        return switch (condition.field()) {
            case ID -> comparablePredicate(
                    root.get("id"),
                    builder,
                    condition.operation(),
                    (Long) condition.value()
            );
            case NAME -> stringPredicate(
                    root.get("name"),
                    builder,
                    condition.operation(),
                    (String) condition.value()
            );
            case TYPE -> equalityPredicate(root.get("type"), builder, condition.operation(), condition.value());
            case STATUS -> equalityPredicate(root.get("status"), builder, condition.operation(), condition.value());
            case CREATED_AT -> comparablePredicate(
                    root.get("createdAt"),
                    builder,
                    condition.operation(),
                    (Instant) condition.value()
            );
            case UPDATED_AT -> comparablePredicate(
                    root.get("updatedAt"),
                    builder,
                    condition.operation(),
                    (Instant) condition.value()
            );
        };
    }

    private Predicate stringPredicate(
            Expression<String> expression,
            CriteriaBuilder builder,
            ResourceSearch.Operation operation,
            String value
    ) {
        if (operation == ResourceSearch.Operation.CONTAINS) {
            return builder.like(expression, "%" + escapeLike(value) + "%", '\\');
        }
        return equalityPredicate(expression, builder, operation, value);
    }

    private Predicate equalityPredicate(
            Expression<?> expression,
            CriteriaBuilder builder,
            ResourceSearch.Operation operation,
            Object value
    ) {
        return operation == ResourceSearch.Operation.EQ
                ? builder.equal(expression, value)
                : builder.notEqual(expression, value);
    }

    private <T extends Comparable<? super T>> Predicate comparablePredicate(
            Expression<? extends T> expression,
            CriteriaBuilder builder,
            ResourceSearch.Operation operation,
            T value
    ) {
        return switch (operation) {
            case EQ -> builder.equal(expression, value);
            case NE -> builder.notEqual(expression, value);
            case GT -> builder.greaterThan(expression, value);
            case GE -> builder.greaterThanOrEqualTo(expression, value);
            case LT -> builder.lessThan(expression, value);
            case LE -> builder.lessThanOrEqualTo(expression, value);
            case CONTAINS -> throw new IllegalStateException("CONTAINS was validated before query creation");
        };
    }

    private List<Order> toOrders(
            Root<ResourceEntity> root,
            CriteriaBuilder builder,
            List<PreparedSort> sort
    ) {
        if (sort.isEmpty()) {
            return List.of(builder.asc(root.get("id")));
        }
        return sort.stream()
                .map(item -> item.order() == ResourceSearch.Direction.ASC
                        ? builder.asc(path(root, item.field()))
                        : builder.desc(path(root, item.field())))
                .toList();
    }

    private Expression<?> path(Root<ResourceEntity> root, SearchField field) {
        return switch (field) {
            case ID -> root.get("id");
            case NAME -> root.get("name");
            case TYPE -> root.get("type");
            case STATUS -> root.get("status");
            case CREATED_AT -> root.get("createdAt");
            case UPDATED_AT -> root.get("updatedAt");
        };
    }

    private String escapeLike(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }

    private InvalidResourceSearchException invalid(String field, String message) {
        return new InvalidResourceSearchException(field, message);
    }

    private enum SearchField {
        ID("id", COMPARISON_OPERATIONS),
        NAME("name", STRING_OPERATIONS),
        TYPE("type", EQUALITY_OPERATIONS),
        STATUS("status", EQUALITY_OPERATIONS),
        CREATED_AT("createdAt", COMPARISON_OPERATIONS),
        UPDATED_AT("updatedAt", COMPARISON_OPERATIONS);

        private final String apiName;
        private final EnumSet<ResourceSearch.Operation> operations;

        SearchField(String apiName, EnumSet<ResourceSearch.Operation> operations) {
            this.apiName = apiName;
            this.operations = operations;
        }

        static SearchField from(String apiName) {
            for (SearchField field : values()) {
                if (field.apiName.equals(apiName)) {
                    return field;
                }
            }
            return null;
        }

        EnumSet<ResourceSearch.Operation> operations() {
            return operations;
        }
    }

    private record PreparedSearch(PreparedFilter filter, List<PreparedSort> sort) {
    }

    private record PreparedFilter(
            ResourceSearch.LogicalOperator operator,
            List<PreparedCondition> conditions
    ) {
    }

    private record PreparedCondition(
            SearchField field,
            ResourceSearch.Operation operation,
            Object value
    ) {
    }

    private record PreparedSort(SearchField field, ResourceSearch.Direction order) {
    }
}
