package com.github.stimur1709.cloudops.probe.execution;

import com.github.stimur1709.cloudops.probe.ProbeErrorCode;
import com.github.stimur1709.cloudops.probe.dns.DnsCheckResult;
import com.github.stimur1709.cloudops.probe.http.HttpCheckResult;
import com.github.stimur1709.cloudops.probe.ping.PingResult;
import com.github.stimur1709.cloudops.probe.port.PortCheckResult;
import com.github.stimur1709.cloudops.probe.ssh.SshCheckResult;
import com.github.stimur1709.cloudops.probe.tls.TlsCheckResult;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(oneOf = {ProbeExecutionResult.Completed.class, ProbeExecutionResult.Failed.class})
public sealed interface ProbeExecutionResult {

    boolean success();

    record Completed(
            boolean success,

            @Schema(
                    oneOf = {
                        HttpCheckResult.class,
                        PortCheckResult.class,
                        DnsCheckResult.class,
                        PingResult.class,
                        TlsCheckResult.class,
                        SshCheckResult.class
                    },
                    description = "Probe-specific successful result selected by the monitor type")
            Object data)
            implements ProbeExecutionResult {}

    record Failed(boolean success, Error error) implements ProbeExecutionResult {
        public Failed(Error error) {
            this(false, error);
        }
    }

    record Error(ProbeErrorCode code, String message) {}

    static Completed completed(boolean success, Object data) {
        return new Completed(success, data);
    }

    static Failed failed(ProbeErrorCode code, String message) {
        return new Failed(new Error(code, message));
    }
}
