package com.github.stimur1709.cloudops.probe.execution;

import com.github.stimur1709.cloudops.probe.ProbeErrorCode;

public sealed interface ProbeExecutionResult {

    record Completed(boolean success, Object data) implements ProbeExecutionResult {
    }

    record Failed(boolean success, Error error) implements ProbeExecutionResult {
        public Failed(Error error) {
            this(false, error);
        }
    }

    record Error(ProbeErrorCode code, String message) {
    }

    static Completed completed(boolean success, Object data) {
        return new Completed(success, data);
    }

    static Failed failed(ProbeErrorCode code, String message) {
        return new Failed(new Error(code, message));
    }
}
