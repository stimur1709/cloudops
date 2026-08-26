package com.github.stimur1709.cloudops.common.persistence.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.Map;

import com.github.stimur1709.cloudops.common.search.InvalidSearchException;
import com.github.stimur1709.cloudops.common.search.SearchQuery;
import org.junit.jupiter.api.Test;

class JpaSearchConfigurationTest {

    @Test
    void buildsDefinitionForAnyEntityFromExplicitFields() {
        JpaSearchField<FutureEntity, Long> id = JpaSearchField.<FutureEntity, Long>comparable(
                root -> root.get("id"),
                SearchValueConverter.longInteger()
        ).allowing(SearchQuery.Operation.EQ, SearchQuery.Operation.GE).sortable();
        JpaSearchField<FutureEntity, String> code = JpaSearchField.<FutureEntity>text(
                root -> root.get("code")
        ).allowing(SearchQuery.Operation.CONTAINS).sortable();

        JpaSearchDefinition<FutureEntity> definition = new JpaSearchDefinition<>(
                FutureEntity.class,
                Map.of("id", id, "code", code),
                "id"
        );

        assertThat(definition.entityType()).isEqualTo(FutureEntity.class);
        assertThat(definition.fields()).containsOnlyKeys("id", "code");
        assertThat(id.supports(SearchQuery.Operation.GE)).isTrue();
        assertThat(code.supports(SearchQuery.Operation.CONTAINS)).isTrue();
        assertThat(code.supports(SearchQuery.Operation.EQ)).isFalse();
    }

    @Test
    void convertsValuesWithoutUsingEntityReflection() {
        JpaSearchField<FutureEntity, Long> id = JpaSearchField.comparable(
                root -> root.get("id"),
                SearchValueConverter.longInteger()
        );

        assertThat(id.convert("42", "filter.conditions[0].value")).isEqualTo(42L);
        assertThatExceptionOfType(InvalidSearchException.class)
                .isThrownBy(() -> id.convert("not-a-number", "filter.conditions[0].value"))
                .satisfies(exception -> {
                    assertThat(exception.field()).isEqualTo("filter.conditions[0].value");
                    assertThat(exception.getMessage()).isEqualTo("Value must be a valid integer number");
                });
    }

    @Test
    void requiresConfiguredSortableDefaultField() {
        JpaSearchField<FutureEntity, String> code = JpaSearchField
                .<FutureEntity>text(root -> root.get("code"));

        assertThatIllegalArgumentException().isThrownBy(() -> new JpaSearchDefinition<>(
                FutureEntity.class,
                Map.of("code", code),
                "code"
        ));
    }

    private static final class FutureEntity {
    }
}
