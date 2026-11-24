package com.heuristiq.uam.google;

import org.springframework.http.HttpStatus;

public class GoogleAuthException extends RuntimeException {
    private final HttpStatus status;

    public GoogleAuthException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
