package com.github.stimur1709.cloudops.organization.persistence;

import static com.github.stimur1709.cloudops.common.search.SearchQuery.Operation.*;

import java.time.Instant;
import java.util.Map;

import com.github.stimur1709.cloudops.common.persistence.search.JpaSearchDefinition;
import com.github.stimur1709.cloudops.common.persistence.search.JpaSearchExecutor;
import com.github.stimur1709.cloudops.common.persistence.search.JpaSearchField;
import com.github.stimur1709.cloudops.common.persistence.search.SearchValueConverter;
import com.github.stimur1709.cloudops.common.search.SearchQuery;
import com.github.stimur1709.cloudops.common.search.SearchResult;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;

@Repository
public class OrganizationSearchRepository {

    private static final JpaSearchDefinition<OrganizationEntity> SEARCH_DEFINITION =
            new JpaSearchDefinition<>(OrganizationEntity.class, Map.of(
                    "id", JpaSearchField.<OrganizationEntity, Long>comparable(
                            root -> root.get("id"), SearchValueConverter.longInteger()
                    ).allowing(EQ, NE, GT, GE, LT, LE).sortable(),
                    "name", JpaSearchField.<OrganizationEntity>text(root -> root.get("name"))
                            .allowing(EQ, NE, CONTAINS).sortable(),
                    "createdAt", JpaSearchField.<OrganizationEntity, Instant>comparable(
                            root -> root.get("createdAt"), SearchValueConverter.instant()
                    ).allowing(EQ, NE, GT, GE, LT, LE).sortable(),
                    "updatedAt", JpaSearchField.<OrganizationEntity, Instant>comparable(
                            root -> root.get("updatedAt"), SearchValueConverter.instant()
                    ).allowing(EQ, NE, GT, GE, LT, LE).sortable()
            ), "id");

    private final JpaSearchExecutor<OrganizationEntity> searchExecutor;

    public OrganizationSearchRepository(EntityManager entityManager) {
        this.searchExecutor = new JpaSearchExecutor<>(entityManager, SEARCH_DEFINITION);
    }

    public SearchResult<OrganizationEntity> search(SearchQuery search) {
        return searchExecutor.search(search);
    }
}
