package com.github.stimur1709.cloudops.task.capability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.github.stimur1709.cloudops.task.TaskType;
import java.util.List;
import org.junit.jupiter.api.Test;

class TaskCapabilityProviderRegistryTest {

    @Test
    void indexesEveryProductionTypeInEnumOrder() {
        TaskCapabilityProvider provider = provider(TaskType.RUN_COMMAND);

        TaskCapabilityProviderRegistry registry = new TaskCapabilityProviderRegistry(List.of(provider));

        assertThat(registry.get(TaskType.RUN_COMMAND)).isSameAs(provider);
        assertThat(registry.all()).containsExactly(provider);
    }

    @Test
    void rejectsDuplicateProvider() {
        assertThatThrownBy(() -> new TaskCapabilityProviderRegistry(
                        List.of(provider(TaskType.RUN_COMMAND), provider(TaskType.RUN_COMMAND))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Duplicate task capability provider");
    }

    @Test
    void rejectsMissingProviderForProductionType() {
        assertThatThrownBy(() -> new TaskCapabilityProviderRegistry(List.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Every task type");
    }

    private TaskCapabilityProvider provider(TaskType type) {
        TaskCapabilityProvider provider = mock(TaskCapabilityProvider.class);
        when(provider.type()).thenReturn(type);
        return provider;
    }
}
