package com.railcab.nms.shared;

/** Thrown when a caller exceeds an allowed rate -- maps to HTTP 429. */
public class NmsTooManyRequestsException extends RuntimeException {

    public NmsTooManyRequestsException(String message) {
        super(message);
    }
}
