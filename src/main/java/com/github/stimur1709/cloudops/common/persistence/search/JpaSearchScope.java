package com.github.stimur1709.cloudops.common.persistence.search;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

@FunctionalInterface
public interface JpaSearchScope<E> {

    Predicate toPredicate(Root<E> root, CriteriaQuery<?> query, CriteriaBuilder builder);
}
