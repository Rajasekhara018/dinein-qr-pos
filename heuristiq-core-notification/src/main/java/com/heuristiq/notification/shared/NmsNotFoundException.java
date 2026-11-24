package com.heuristiq.notification.shared;

/** Thrown when a lookup by id/key finds no matching record -- maps to HTTP 404. */
public class NmsNotFoundException extends RuntimeException {

    public NmsNotFoundException(String message) {
        super(message);
    }
}
