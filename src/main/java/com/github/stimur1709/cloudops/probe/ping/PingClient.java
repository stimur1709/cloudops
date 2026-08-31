package com.github.stimur1709.cloudops.probe.ping;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;

import com.github.stimur1709.cloudops.probe.ProbeErrorCode;
import org.springframework.stereotype.Component;

@Component
public class PingClient {

    private final Duration timeout;
    private final Reachability reachability;

    public PingClient() {
        this(Duration.ZERO, PingClient::isReachable);
    }

    PingClient(Duration timeout, Reachability reachability) {
        this.timeout = timeout;
        this.reachability = reachability;
    }

    PingOutcome execute(String host) {
        return execute(host, Math.toIntExact(timeout.toMillis()));
    }

    PingOutcome execute(String host, int timeoutMs) {
        long startedAt = System.nanoTime();
        try {
            boolean reachable = reachability.isReachable(host, timeoutMs);
            long responseTimeMs = Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
            if (reachable) {
                return PingOutcome.completed(new PingResult(host, responseTimeMs));
            }
            return PingOutcome.failed(ProbeErrorCode.TIMEOUT, "Host did not respond before the timeout");
        } catch (UnknownHostException exception) {
            return PingOutcome.failed(ProbeErrorCode.DNS_ERROR, "Host name could not be resolved");
        } catch (IOException exception) {
            return PingOutcome.failed(ProbeErrorCode.CONNECTION_ERROR, "Reachability check could not be completed");
        }
    }

    private static boolean isReachable(String host, int timeoutMs) throws IOException {
        return InetAddress.getByName(host).isReachable(timeoutMs);
    }

    @FunctionalInterface
    interface Reachability {
        boolean isReachable(String host, int timeoutMs) throws IOException;
    }
}
