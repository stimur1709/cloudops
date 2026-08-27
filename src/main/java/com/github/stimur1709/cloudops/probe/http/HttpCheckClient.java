package com.github.stimur1709.cloudops.probe.http;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import javax.net.ssl.SSLException;

import com.github.stimur1709.cloudops.probe.ProbeErrorCode;
import com.github.stimur1709.cloudops.resource.config.ServiceResourceConfig;
import org.springframework.stereotype.Component;

@Component
public class HttpCheckClient {

    private final HttpClient client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public HttpCheckOutcome execute(ServiceResourceConfig config) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(config.url()))
                .timeout(Duration.ofMillis(config.timeoutMs()))
                .GET()
                .build();
        long startedAt = System.nanoTime();
        try {
            HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
            long responseTimeMs = Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
            return HttpCheckOutcome.completed(new HttpCheckResult(
                    config.url(), response.statusCode(), config.expectedStatus(), responseTimeMs,
                    response.statusCode() == config.expectedStatus()
            ));
        } catch (HttpTimeoutException exception) {
            return failure(ProbeErrorCode.TIMEOUT, "HTTP check timed out");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return failure(ProbeErrorCode.HTTP_CLIENT_ERROR, "HTTP check was interrupted");
        } catch (IOException exception) {
            return classify(exception);
        }
    }

    HttpCheckOutcome classify(IOException exception) {
        if (hasCause(exception, UnknownHostException.class)) {
            return failure(ProbeErrorCode.DNS_ERROR, "Host name could not be resolved");
        }
        if (hasCause(exception, SSLException.class)) {
            return failure(ProbeErrorCode.TLS_ERROR, "TLS connection could not be established");
        }
        if (hasCause(exception, ConnectException.class)) {
            return failure(ProbeErrorCode.CONNECTION_ERROR, "Connection could not be established");
        }
        return failure(ProbeErrorCode.HTTP_CLIENT_ERROR, "HTTP check could not be completed");
    }

    private boolean hasCause(Throwable throwable, Class<? extends Throwable> type) {
        Throwable current = throwable;
        while (current != null) {
            if (type.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private HttpCheckOutcome failure(ProbeErrorCode code, String message) {
        return HttpCheckOutcome.failed(code, message);
    }
}
