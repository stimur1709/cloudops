package com.github.stimur1709.cloudops.task.parameters;

import com.github.stimur1709.cloudops.task.TaskType;
import jakarta.validation.Validator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class TaskParameterCodecRegistry {

    private final ObjectMapper objectMapper;
    private final Validator validator;
    private final Map<TaskType, TaskParameterDefinition<?>> definitions;

    public TaskParameterCodecRegistry(
            ObjectMapper objectMapper, Validator validator, List<TaskParameterDefinition<?>> definitions) {
        this.objectMapper = objectMapper;
        this.validator = validator;
        EnumMap<TaskType, TaskParameterDefinition<?>> indexed = new EnumMap<>(TaskType.class);
        for (TaskParameterDefinition<?> definition : definitions) {
            if (indexed.put(definition.type(), definition) != null) {
                throw new IllegalStateException("Duplicate task parameter definition for " + definition.type());
            }
        }
        if (!indexed.keySet().equals(java.util.EnumSet.allOf(TaskType.class))) {
            throw new IllegalStateException("Every task type must have exactly one parameter definition");
        }
        this.definitions = Map.copyOf(indexed);
    }

    public TaskParameters decode(TaskType type, JsonNode parameters) {
        TaskParameterDefinition<?> definition = definitions.get(type);
        if (definition == null) {
            throw invalid("parameters", "Parameters are not configured for task type " + type);
        }
        if (parameters == null || !parameters.isObject()) {
            throw invalid("parameters", "Parameters are required");
        }
        java.util.Set<String> allowedFields = java.util.Arrays.stream(
                        definition.parametersType().getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        if (!allowedFields.containsAll(parameters.propertyNames())) {
            throw invalid("parameters", "Parameters contain unknown fields");
        }

        final TaskParameters decoded;
        try {
            decoded = objectMapper.treeToValue(parameters, definition.parametersType());
        } catch (RuntimeException exception) {
            throw invalid("parameters", "Parameters contain unknown or invalid fields");
        }

        List<TaskParameterValidationException.ParameterError> errors = validator.validate(decoded).stream()
                .map(violation -> new TaskParameterValidationException.ParameterError(
                        "parameters." + violation.getPropertyPath(), violation.getMessage()))
                .sorted(java.util.Comparator.comparing(TaskParameterValidationException.ParameterError::field))
                .toList();
        if (!errors.isEmpty()) {
            throw new TaskParameterValidationException(errors);
        }
        return decoded;
    }

    public <P extends TaskParameters> P decode(TaskType type, JsonNode parameters, Class<P> expectedType) {
        TaskParameters decoded = decode(type, parameters);
        if (!expectedType.isInstance(decoded)) {
            throw new IllegalStateException("Unexpected parameters type for " + type);
        }
        return expectedType.cast(decoded);
    }

    public JsonNode encode(TaskParameters parameters) {
        return objectMapper.valueToTree(parameters);
    }

    private TaskParameterValidationException invalid(String field, String message) {
        return new TaskParameterValidationException(
                List.of(new TaskParameterValidationException.ParameterError(field, message)));
    }
}
