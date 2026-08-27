package com.github.stimur1709.cloudops.task.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.time.Instant;

import com.github.stimur1709.cloudops.task.TaskErrorCode;
import com.github.stimur1709.cloudops.task.TaskStatus;
import com.github.stimur1709.cloudops.task.TaskType;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class TaskEntityTest {

    private static final Instant CREATED = Instant.parse("2026-08-27T01:00:00Z");
    private static final Instant STARTED = Instant.parse("2026-08-27T01:00:01Z");
    private static final Instant COMPLETED = Instant.parse("2026-08-27T01:00:02Z");

    @Test
    void supportsCompletedLifecycle() {
        TaskEntity task = TaskEntity.create(1, 2, TaskType.HTTP_CHECK, 3, CREATED);
        task.start(STARTED);
        task.complete(new ObjectMapper().createObjectNode().put("statusCode", 200), COMPLETED);

        assertThat(task.status()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(task.startedAt()).isEqualTo(STARTED);
        assertThat(task.completedAt()).isEqualTo(COMPLETED);
        assertThat(task.result().get("statusCode").asInt()).isEqualTo(200);
    }

    @Test
    void supportsFailedLifecycle() {
        TaskEntity task = TaskEntity.create(1, 2, TaskType.HTTP_CHECK, 3, CREATED);
        task.start(STARTED);
        task.fail(TaskErrorCode.TIMEOUT, "HTTP check timed out", COMPLETED);

        assertThat(task.status()).isEqualTo(TaskStatus.FAILED);
        assertThat(task.errorCode()).isEqualTo(TaskErrorCode.TIMEOUT);
    }

    @Test
    void rejectsInvalidTransitions() {
        TaskEntity task = TaskEntity.create(1, 2, TaskType.HTTP_CHECK, 3, CREATED);

        assertThatIllegalStateException().isThrownBy(() -> task.complete(null, COMPLETED));
        task.start(STARTED);
        assertThatIllegalStateException().isThrownBy(() -> task.start(STARTED));
    }
}
