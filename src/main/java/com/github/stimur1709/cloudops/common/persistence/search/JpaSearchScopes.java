package com.github.stimur1709.cloudops.common.persistence.search;

import jakarta.persistence.metamodel.SingularAttribute;

public final class JpaSearchScopes {

    private JpaSearchScopes() {
    }

    public static <E, V> JpaSearchScope<E> equal(
            SingularAttribute<? super E, V> attribute,
            V value
    ) {
        return (root, _, builder) -> builder.equal(root.get(attribute), value);
    }
}
