package com.railcab.nms.shared;

/** Thrown when a change would clash with a record that already exists (a duplicate event value) -- maps to HTTP 409. */
public class NmsConflictException extends RuntimeException {

    public NmsConflictException(String message) {
        super(message);
    }
}
