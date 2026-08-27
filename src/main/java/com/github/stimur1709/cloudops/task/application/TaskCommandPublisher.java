package com.github.stimur1709.cloudops.task.application;

public interface TaskCommandPublisher {

    void publish(long taskId);
}
