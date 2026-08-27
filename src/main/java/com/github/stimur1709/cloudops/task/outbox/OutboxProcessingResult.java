package com.github.stimur1709.cloudops.task.outbox;

enum OutboxProcessingResult {
    PUBLISHED,
    FAILED,
    SKIPPED
}
