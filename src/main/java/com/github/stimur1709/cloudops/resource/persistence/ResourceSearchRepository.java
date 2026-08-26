package com.github.stimur1709.cloudops.resource.persistence;

import static com.github.stimur1709.cloudops.common.search.SearchQuery.Operation.CONTAINS;
import static com.github.stimur1709.cloudops.common.search.SearchQuery.Operation.EQ;
import static com.github.stimur1709.cloudops.common.search.SearchQuery.Operation.GE;
import static com.github.stimur1709.cloudops.common.search.SearchQuery.Operation.GT;
import static com.github.stimur1709.cloudops.common.search.SearchQuery.Operation.LE;
import static com.github.stimur1709.cloudops.common.search.SearchQuery.Operation.LT;
import static com.github.stimur1709.cloudops.common.search.SearchQuery.Operation.NE;

import java.time.Instant;
import java.util.Map;

import com.github.stimur1709.cloudops.common.persistence.search.JpaSearchDefinition;
import com.github.stimur1709.cloudops.common.persistence.search.JpaSearchExecutor;
import com.github.stimur1709.cloudops.common.persistence.search.JpaSearchField;
import com.github.stimur1709.cloudops.common.persistence.search.SearchValueConverter;
import com.github.stimur1709.cloudops.common.search.SearchQuery;
import com.github.stimur1709.cloudops.common.search.SearchResult;
import com.github.stimur1709.cloudops.resource.ResourceStatus;
import com.github.stimur1709.cloudops.resource.ResourceType;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;

@Repository
public class ResourceSearchRepository {

    private static final JpaSearchDefinition<ResourceEntity> SEARCH_DEFINITION =
            new JpaSearchDefinition<>(
                    ResourceEntity.class,
                    Map.of(
                            "id",
                            JpaSearchField.<ResourceEntity, Long>comparable(
                                    root -> root.get("id"),
                                    SearchValueConverter.longInteger()
                            ).allowing(EQ, NE, GT, GE, LT, LE).sortable(),
                            "name",
                            JpaSearchField.<ResourceEntity>text(root -> root.get("name"))
                                    .allowing(EQ, NE, CONTAINS)
                                    .sortable(),
                            "type",
                            JpaSearchField.<ResourceEntity, ResourceType>equality(
                                    root -> root.get("type"),
                                    SearchValueConverter.enumeration(ResourceType.class)
                            ).allowing(EQ, NE).sortable(),
                            "status",
                            JpaSearchField.<ResourceEntity, ResourceStatus>equality(
                                    root -> root.get("status"),
                                    SearchValueConverter.enumeration(ResourceStatus.class)
                            ).allowing(EQ, NE).sortable(),
                            "createdAt",
                            JpaSearchField.<ResourceEntity, Instant>comparable(
                                    root -> root.get("createdAt"),
                                    SearchValueConverter.instant()
                            ).allowing(EQ, NE, GT, GE, LT, LE).sortable(),
                            "updatedAt",
                            JpaSearchField.<ResourceEntity, Instant>comparable(
                                    root -> root.get("updatedAt"),
                                    SearchValueConverter.instant()
                            ).allowing(EQ, NE, GT, GE, LT, LE).sortable()
                    ),
                    "id"
            );

    private final JpaSearchExecutor<ResourceEntity> searchExecutor;

    public ResourceSearchRepository(EntityManager entityManager) {
        this.searchExecutor = new JpaSearchExecutor<>(entityManager, SEARCH_DEFINITION);
    }

    public SearchResult<ResourceEntity> search(SearchQuery search) {
        return searchExecutor.search(search);
    }
}
