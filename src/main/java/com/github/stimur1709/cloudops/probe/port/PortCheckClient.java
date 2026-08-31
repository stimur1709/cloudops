package com.github.stimur1709.cloudops.probe.port;

import com.github.stimur1709.cloudops.probe.ProbeErrorCode;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.time.Duration;
import org.springframework.stereotype.Component;

@Component
public class PortCheckClient {

    private final Duration timeout;
    private final Connector connector;

    public PortCheckClient() {
        this(Duration.ZERO, PortCheckClient::connect);
    }

    PortCheckClient(Duration timeout) {
        this(timeout, PortCheckClient::connect);
    }

    PortCheckClient(Duration timeout, Connector connector) {
        this.timeout = timeout;
        this.connector = connector;
    }

    PortCheckOutcome execute(String host, int port) {
        return execute(host, port, Math.toIntExact(timeout.toMillis()));
    }

    PortCheckOutcome execute(String host, int port, int timeoutMs) {
        long startedAt = System.nanoTime();
        try {
            connector.connect(host, port, timeoutMs);
            long responseTimeMs =
                    Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
            return PortCheckOutcome.completed(new PortCheckResult(host, port, responseTimeMs));
        } catch (UnknownHostException exception) {
            return PortCheckOutcome.failed(ProbeErrorCode.DNS_ERROR, "Host name could not be resolved");
        } catch (SocketTimeoutException exception) {
            return PortCheckOutcome.failed(ProbeErrorCode.TIMEOUT, "Port check timed out");
        } catch (IOException exception) {
            return PortCheckOutcome.failed(ProbeErrorCode.CONNECTION_ERROR, "Connection could not be established");
        }
    }

    private static void connect(String host, int port, int timeoutMs) throws IOException {
        InetAddress address = InetAddress.getByName(host);
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(address, port), timeoutMs);
        }
    }

    @FunctionalInterface
    interface Connector {
        void connect(String host, int port, int timeoutMs) throws IOException;
    }
}
