package com.github.stimur1709.cloudops.probe.http;

import com.github.stimur1709.cloudops.probe.ProbeType;
import com.github.stimur1709.cloudops.probe.execution.ProbeExecutionContext;
import com.github.stimur1709.cloudops.probe.execution.ProbeExecutionResult;
import com.github.stimur1709.cloudops.probe.execution.ProbeHandler;
import com.github.stimur1709.cloudops.resource.config.ResourceConfig;
import com.github.stimur1709.cloudops.resource.config.ServiceResourceConfig;
import org.springframework.stereotype.Component;

@Component
public class HttpCheckHandler implements ProbeHandler {

    private final HttpCheckClient httpCheckClient;

    public HttpCheckHandler(HttpCheckClient httpCheckClient) {
        this.httpCheckClient = httpCheckClient;
    }

    @Override
    public ProbeType supports() {
        return ProbeType.HTTP_CHECK;
    }

    @Override
    public ProbeExecutionResult execute(ProbeExecutionContext context) {
        HttpCheckOutcome outcome = httpCheckClient.execute(serviceConfig(context.resourceConfig()));
        if (outcome.completed()) {
            return ProbeExecutionResult.completed(outcome.result().matchedExpectedStatus(), outcome.result());
        }
        return ProbeExecutionResult.failed(outcome.errorCode(), outcome.errorMessage());
    }

    private ServiceResourceConfig serviceConfig(ResourceConfig config) {
        if (config instanceof ServiceResourceConfig serviceConfig) {
            return serviceConfig;
        }
        throw new IllegalArgumentException("HTTP_CHECK requires SERVICE resource configuration");
    }
}
