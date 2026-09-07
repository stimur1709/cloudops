package com.github.stimur1709.cloudops.task.capability;

import com.github.stimur1709.cloudops.task.TaskType;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class TaskCapabilityProviderRegistry {

    private final Map<TaskType, TaskCapabilityProvider> providers;

    public TaskCapabilityProviderRegistry(List<TaskCapabilityProvider> providers) {
        EnumMap<TaskType, TaskCapabilityProvider> indexed = new EnumMap<>(TaskType.class);
        for (TaskCapabilityProvider provider : providers) {
            if (indexed.put(provider.type(), provider) != null) {
                throw new IllegalStateException("Duplicate task capability provider for " + provider.type());
            }
        }
        if (!indexed.keySet().equals(EnumSet.allOf(TaskType.class))) {
            throw new IllegalStateException("Every task type must have exactly one capability provider");
        }
        this.providers = Map.copyOf(indexed);
    }

    public TaskCapabilityProvider get(TaskType type) {
        TaskCapabilityProvider provider = providers.get(type);
        if (provider == null) {
            throw new IllegalStateException("Task capability provider is not configured for " + type);
        }
        return provider;
    }

    public List<TaskCapabilityProvider> all() {
        return Arrays.stream(TaskType.values()).map(providers::get).toList();
    }
}
