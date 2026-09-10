package com.github.stimur1709.cloudops.common.search;

public final class InvalidSearchException extends RuntimeException {

    private final String field;

    public InvalidSearchException(String field, String message) {
        super(message);
        this.field = field;
    }

    public InvalidSearchException(String field, String message, Throwable cause) {
        super(message, cause);
        this.field = field;
    }

    public String field() {
        return field;
    }
}
