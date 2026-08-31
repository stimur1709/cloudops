package com.github.stimur1709.cloudops.credential.persistence;

import java.time.Instant;

import com.github.stimur1709.cloudops.common.persistence.search.JpaSearchDefinition;
import com.github.stimur1709.cloudops.common.persistence.search.JpaSearchField;
import com.github.stimur1709.cloudops.common.persistence.search.SearchValueConverter;
import com.github.stimur1709.cloudops.credential.CredentialType;

public final class CredentialSearchDefinition {
    public static final JpaSearchDefinition<CredentialEntity> DEFINITION =
            JpaSearchDefinition.builder(CredentialEntity.class)
                    .field(
                            CredentialEntity_.ID,
                            JpaSearchField.<CredentialEntity, Long>comparable(
                                    root -> root.get(CredentialEntity_.ID),
                                    SearchValueConverter.longInteger()
                            ).sortable()
                    )
                    .field(
                            CredentialEntity_.ORGANIZATION_ID,
                            JpaSearchField.<CredentialEntity, Long>comparable(
                                    root -> root.get(CredentialEntity_.ORGANIZATION_ID),
                                    SearchValueConverter.longInteger()
                            ).sortable()
                    )
                    .field(
                            CredentialEntity_.NAME,
                            JpaSearchField.<CredentialEntity>text(
                                    root -> root.get(CredentialEntity_.NAME)
                            ).sortable())
                    .field(
                            CredentialEntity_.TYPE,
                            JpaSearchField.<CredentialEntity, CredentialType>equality(
                                    root -> root.get(CredentialEntity_.TYPE),
                                    SearchValueConverter.enumeration(CredentialType.class)
                            ).sortable())
                    .field(
                            CredentialEntity_.USERNAME,
                            JpaSearchField.<CredentialEntity>text(
                                    root -> root.get(CredentialEntity_.USERNAME)
                            ).sortable())
                    .field(
                            CredentialEntity_.CREATED_AT,
                            JpaSearchField.<CredentialEntity, Instant>comparable(
                                    root -> root.get(CredentialEntity_.CREATED_AT),
                                    SearchValueConverter.instant()).sortable()
                    )
                    .field(
                            CredentialEntity_.UPDATED_AT,
                            JpaSearchField.<CredentialEntity, Instant>comparable(
                                    root -> root.get(CredentialEntity_.UPDATED_AT),
                                    SearchValueConverter.instant()
                            ).sortable())
                    .defaultSort(CredentialEntity_.ID).build();

    private CredentialSearchDefinition() {
    }
}
