package com.github.stimur1709.cloudops.common.persistence.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.metamodel.SingularAttribute;
import org.junit.jupiter.api.Test;

class JpaSearchScopeTest {

    @Test
    @SuppressWarnings("unchecked")
    void createsTypedEqualityScope() {
        Root<TestEntity> root = mock(Root.class);
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        CriteriaBuilder builder = mock(CriteriaBuilder.class);
        SingularAttribute<TestEntity, Long> attribute = mock(SingularAttribute.class);
        Path<Long> path = mock(Path.class);
        Predicate predicate = mock(Predicate.class);
        when(root.get(attribute)).thenReturn(path);
        when(builder.equal(path, 42L)).thenReturn(predicate);

        JpaSearchScope<TestEntity> scope = JpaSearchScopes.equal(attribute, 42L);

        assertThat(scope.toPredicate(root, query, builder)).isSameAs(predicate);
    }

    @Test
    @SuppressWarnings("unchecked")
    void combinesServerScopesWithAnd() {
        Root<TestEntity> root = mock(Root.class);
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        CriteriaBuilder builder = mock(CriteriaBuilder.class);
        Predicate firstPredicate = mock(Predicate.class);
        Predicate secondPredicate = mock(Predicate.class);
        Predicate combinedPredicate = mock(Predicate.class);
        JpaSearchScope<TestEntity> first = (ignoredRoot, ignoredQuery, ignoredBuilder) -> firstPredicate;
        JpaSearchScope<TestEntity> second = (ignoredRoot, ignoredQuery, ignoredBuilder) -> secondPredicate;
        when(builder.and(firstPredicate, secondPredicate)).thenReturn(combinedPredicate);

        Predicate result = first.and(second).toPredicate(root, query, builder);

        assertThat(result).isSameAs(combinedPredicate);
    }

    private static final class TestEntity {
    }
}
