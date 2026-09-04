package com.github.stimur1709.cloudops.task.parameters;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.stimur1709.cloudops.task.TaskType;
import com.github.stimur1709.cloudops.task.runcommand.RunCommandParameters;
import com.github.stimur1709.cloudops.task.runcommand.RunCommandTaskDefinition;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class TaskParameterCodecRegistryTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final TaskParameterCodecRegistry registry = new TaskParameterCodecRegistry(
            objectMapper,
            Validation.buildDefaultValidatorFactory().getValidator(),
            java.util.List.of(new RunCommandTaskDefinition()));

    @Test
    void deserializesTypedParameters() {
        RunCommandParameters parameters = registry.decode(
                TaskType.RUN_COMMAND,
                objectMapper.createObjectNode().put("command", "uname -a"),
                RunCommandParameters.class);

        assertThat(parameters.command()).isEqualTo("uname -a");
    }

    @Test
    void rejectsMissingBlankUnknownAndTooLongCommands() {
        assertInvalid(objectMapper.createObjectNode(), "parameters.command");
        assertInvalid(objectMapper.createObjectNode().put("command", " "), "parameters.command");
        assertInvalid(objectMapper.createObjectNode().put("command", "true").put("unknown", 1), "parameters");
        assertInvalid(objectMapper.createObjectNode().put("command", "x".repeat(4097)), "parameters.command");
    }

    private void assertInvalid(tools.jackson.databind.JsonNode parameters, String field) {
        assertThatThrownBy(() -> registry.decode(TaskType.RUN_COMMAND, parameters))
                .isInstanceOfSatisfying(
                        TaskParameterValidationException.class,
                        exception -> assertThat(exception.errors())
                                .extracting(TaskParameterValidationException.ParameterError::field)
                                .contains(field));
    }
}
