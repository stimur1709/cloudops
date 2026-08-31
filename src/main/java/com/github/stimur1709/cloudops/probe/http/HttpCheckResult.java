package com.github.stimur1709.cloudops.probe.http;

public record HttpCheckResult(
        String url, int statusCode, int expectedStatus, long responseTimeMs, boolean matchedExpectedStatus) {}
