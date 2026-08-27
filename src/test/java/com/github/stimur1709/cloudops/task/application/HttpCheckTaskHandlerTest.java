package com.github.stimur1709.cloudops.task.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.github.stimur1709.cloudops.resource.config.ServiceResourceConfig;
import com.github.stimur1709.cloudops.task.TaskType;
import org.junit.jupiter.api.Test;

class HttpCheckTaskHandlerTest {

    @Test
    void executesHttpCheckAndReturnsTypedResult() {
        HttpCheckClient client = mock(HttpCheckClient.class);
        ServiceResourceConfig config = new ServiceResourceConfig("https://example.com", 204, 1000);
        HttpCheckResult checkResult = new HttpCheckResult("https://example.com", 204, 204, 10, true);
        when(client.execute(config)).thenReturn(HttpCheckOutcome.completed(checkResult));
        HttpCheckTaskHandler handler = new HttpCheckTaskHandler(client);

        TaskExecutionResult result = handler.execute(
                new TaskExecutionContext(1, 2, TaskType.HTTP_CHECK, config)
        );

        assertThat(handler.supports()).isEqualTo(TaskType.HTTP_CHECK);
        assertThat(result).isEqualTo(TaskExecutionResult.completed(checkResult));
        verify(client).execute(config);
    }
}
