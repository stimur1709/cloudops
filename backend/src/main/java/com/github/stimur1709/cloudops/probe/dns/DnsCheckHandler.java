package com.github.stimur1709.cloudops.probe.dns;

import com.github.stimur1709.cloudops.probe.ProbeType;
import com.github.stimur1709.cloudops.probe.ResourceHostExtractor;
import com.github.stimur1709.cloudops.probe.execution.ProbeExecutionContext;
import com.github.stimur1709.cloudops.probe.execution.ProbeExecutionResult;
import com.github.stimur1709.cloudops.probe.execution.ProbeHandler;
import com.github.stimur1709.cloudops.resource.config.ResourceConfig;
import org.springframework.stereotype.Component;

@Component
public class DnsCheckHandler implements ProbeHandler {

    private final DnsCheckClient client;

    public DnsCheckHandler(DnsCheckClient client) {
        this.client = client;
    }

    @Override
    public ProbeType type() {
        return ProbeType.DNS_CHECK;
    }

    @Override
    public boolean isCompatibleWith(ResourceConfig resourceConfig) {
        String hostname = ResourceHostExtractor.extract(resourceConfig);
        return hostname != null && !IpLiteral.isIpLiteral(hostname);
    }

    @Override
    public ProbeExecutionResult execute(ProbeExecutionContext context) {
        String hostname = ResourceHostExtractor.extract(context.resourceConfig());
        if (hostname == null || IpLiteral.isIpLiteral(hostname)) {
            throw new IllegalArgumentException("DNS_CHECK requires a resource configuration with a host name");
        }
        DnsCheckOutcome outcome = client.execute(hostname);
        if (outcome.completed()) {
            return ProbeExecutionResult.completed(true, outcome.result());
        }
        return ProbeExecutionResult.failed(outcome.errorCode(), outcome.errorMessage());
    }
}
