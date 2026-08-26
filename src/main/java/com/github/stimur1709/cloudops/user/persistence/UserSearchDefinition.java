package com.github.stimur1709.cloudops.user.persistence;

import java.time.Instant;

import com.github.stimur1709.cloudops.common.persistence.search.JpaSearchDefinition;
import com.github.stimur1709.cloudops.common.persistence.search.JpaSearchField;
import com.github.stimur1709.cloudops.common.persistence.search.SearchValueConverter;

public final class UserSearchDefinition {

    public static final JpaSearchDefinition<UserEntity> DEFINITION =
            JpaSearchDefinition.builder(UserEntity.class)
                    .field(UserEntity_.ID, JpaSearchField.<UserEntity, Long>comparable(
                            root -> root.get(UserEntity_.id), SearchValueConverter.longInteger()
                    ).sortable())
                    .field(UserEntity_.EMAIL, JpaSearchField.<UserEntity>text(
                            root -> root.get(UserEntity_.email)
                    ).sortable())
                    .field(UserEntity_.DISPLAY_NAME, JpaSearchField.<UserEntity>text(
                            root -> root.get(UserEntity_.displayName)
                    ).sortable())
                    .field(UserEntity_.CREATED_AT, JpaSearchField.<UserEntity, Instant>comparable(
                            root -> root.get(UserEntity_.createdAt), SearchValueConverter.instant()
                    ).sortable())
                    .field(UserEntity_.UPDATED_AT, JpaSearchField.<UserEntity, Instant>comparable(
                            root -> root.get(UserEntity_.updatedAt), SearchValueConverter.instant()
                    ).sortable())
                    .defaultSort(UserEntity_.ID)
                    .build();

    private UserSearchDefinition() {
    }
}
