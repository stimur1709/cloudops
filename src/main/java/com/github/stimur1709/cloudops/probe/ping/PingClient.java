package com.github.stimur1709.cloudops.probe.ping;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;

import com.github.stimur1709.cloudops.probe.ProbeErrorCode;
import com.github.stimur1709.cloudops.probe.config.PingProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class PingClient {

    private final Duration timeout;
    private final Reachability reachability;

    @Autowired
    public PingClient(PingProperties properties) {
        this(properties.timeout(), PingClient::isReachable);
    }

    PingClient(Duration timeout, Reachability reachability) {
        this.timeout = timeout;
        this.reachability = reachability;
    }

    PingOutcome execute(String host) {
        long startedAt = System.nanoTime();
        try {
            boolean reachable = reachability.isReachable(host, Math.toIntExact(timeout.toMillis()));
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
