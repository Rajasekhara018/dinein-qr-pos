package com.heuristiq.uam.identity;

import org.springframework.http.HttpStatus;

public class ProviderAuthException extends RuntimeException {
    private final HttpStatus status;

    public ProviderAuthException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
