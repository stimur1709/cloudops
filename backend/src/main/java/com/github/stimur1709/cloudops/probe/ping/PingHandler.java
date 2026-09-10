package com.github.stimur1709.cloudops.probe.ping;

import com.github.stimur1709.cloudops.probe.ProbeType;
import com.github.stimur1709.cloudops.probe.ResourceHostExtractor;
import com.github.stimur1709.cloudops.probe.execution.ProbeExecutionContext;
import com.github.stimur1709.cloudops.probe.execution.ProbeExecutionResult;
import com.github.stimur1709.cloudops.probe.execution.ProbeHandler;
import com.github.stimur1709.cloudops.resource.config.ResourceConfig;
import org.springframework.stereotype.Component;

@Component
public class PingHandler implements ProbeHandler {

    private final PingClient client;

    public PingHandler(PingClient client) {
        this.client = client;
    }

    @Override
    public ProbeType type() {
        return ProbeType.PING;
    }

    @Override
    public boolean isCompatibleWith(ResourceConfig resourceConfig) {
        return ResourceHostExtractor.extract(resourceConfig) != null;
    }

    @Override
    public ProbeExecutionResult execute(ProbeExecutionContext context) {
        String host = ResourceHostExtractor.extract(context.resourceConfig());
        if (host == null) {
            throw new IllegalArgumentException("PING requires a resource configuration with a host");
        }
        PingOutcome outcome = client.execute(host, context.timeoutMs());
        if (outcome.completed()) {
            return ProbeExecutionResult.completed(true, outcome.result());
        }
        return ProbeExecutionResult.failed(outcome.errorCode(), outcome.errorMessage());
    }
}
