package com.heuristiq.uam.common;

import org.springframework.http.HttpStatus;

public class UamException extends RuntimeException {
    private final HttpStatus status;
    private final String errorCode;

    public UamException(String message) {
        super(message);
        this.status = HttpStatus.BAD_REQUEST;
        this.errorCode = null;
    }

    public UamException(String message, HttpStatus status) {
        super(message);
        this.status = status;
        this.errorCode = null;
    }

    /** @param errorCode a machine-readable code the client can branch on (sent as {@code errorCode} in the response envelope) */
    public UamException(String message, HttpStatus status, String errorCode) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }

    public static UamException notFound(String message) {
        return new UamException(message, HttpStatus.NOT_FOUND);
    }

    public static UamException conflict(String message) {
        return new UamException(message, HttpStatus.CONFLICT);
    }

    public HttpStatus getStatus() {
        return status;
    }

    /** The machine-readable code, or null when this failure carries none. */
    public String getErrorCode() {
        return errorCode;
    }
}
