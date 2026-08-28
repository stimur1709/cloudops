package com.github.stimur1709.cloudops.probe.dns;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;

import org.springframework.stereotype.Component;

@Component
public class DnsCheckClient {

    private final Resolver resolver;

    public DnsCheckClient() {
        this(InetAddress::getAllByName);
    }

    DnsCheckClient(Resolver resolver) {
        this.resolver = resolver;
    }

    DnsCheckOutcome execute(String hostname) {
        long startedAt = System.nanoTime();
        try {
            List<String> addresses = Arrays.stream(resolver.resolve(hostname))
                    .map(InetAddress::getHostAddress)
                    .distinct()
                    .toList();
            long responseTimeMs = Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
            return DnsCheckOutcome.completed(new DnsCheckResult(hostname, addresses, responseTimeMs));
        } catch (UnknownHostException exception) {
            return DnsCheckOutcome.failed("Host name could not be resolved");
        }
    }

    @FunctionalInterface
    interface Resolver {
        InetAddress[] resolve(String hostname) throws UnknownHostException;
    }
}
