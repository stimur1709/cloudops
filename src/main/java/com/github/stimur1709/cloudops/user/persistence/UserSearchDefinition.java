package com.github.stimur1709.cloudops.user.persistence;

import java.time.Instant;
import com.github.stimur1709.cloudops.common.persistence.search.JpaSearchDefinition;
import com.github.stimur1709.cloudops.common.persistence.search.JpaSearchField;
import com.github.stimur1709.cloudops.common.persistence.search.SearchValueConverter;

public final class UserSearchDefinition {

    public static final JpaSearchDefinition<UserEntity> DEFINITION =
            JpaSearchDefinition.builder(UserEntity.class)
                    .field("id", JpaSearchField.<UserEntity, Long>comparable(
                            root -> root.get("id"), SearchValueConverter.longInteger()
                    ).sortable())
                    .field("email", JpaSearchField.<UserEntity>text(root -> root.get("email")).sortable())
                    .field("displayName", JpaSearchField.<UserEntity>text(
                            root -> root.get("displayName")
                    ).sortable())
                    .field("createdAt", JpaSearchField.<UserEntity, Instant>comparable(
                            root -> root.get("createdAt"), SearchValueConverter.instant()
                    ).sortable())
                    .field("updatedAt", JpaSearchField.<UserEntity, Instant>comparable(
                            root -> root.get("updatedAt"), SearchValueConverter.instant()
                    ).sortable())
                    .defaultSort("id")
                    .build();

    private UserSearchDefinition() {
    }
}
