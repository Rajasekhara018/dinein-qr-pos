package com.heuristiq.documents.exception;

import org.springframework.http.HttpStatus;

public class DmsException extends RuntimeException {

    private final HttpStatus status;
    private final String reasonCode;

    private DmsException(HttpStatus status, String message, String reasonCode) {
        super(message);
        this.status = status;
        this.reasonCode = reasonCode;
    }

    public HttpStatus getStatus() {
        return status;
    }

    /** Stable machine code for the failure (see {@code DocumentReasonCodes}), or null when none was given. */
    public String getReasonCode() {
        return reasonCode;
    }

    public static DmsException badRequest(String message) {
        return new DmsException(HttpStatus.BAD_REQUEST, message, null);
    }

    public static DmsException badRequest(String message, String reasonCode) {
        return new DmsException(HttpStatus.BAD_REQUEST, message, reasonCode);
    }

    public static DmsException forbidden(String message) {
        return new DmsException(HttpStatus.FORBIDDEN, message, null);
    }

    public static DmsException forbidden(String message, String reasonCode) {
        return new DmsException(HttpStatus.FORBIDDEN, message, reasonCode);
    }

    public static DmsException unauthorized(String message) {
        return new DmsException(HttpStatus.UNAUTHORIZED, message, null);
    }

    public static DmsException serviceUnavailable(String message) {
        return new DmsException(HttpStatus.SERVICE_UNAVAILABLE, message, null);
    }

    public static DmsException serviceUnavailable(String message, String reasonCode) {
        return new DmsException(HttpStatus.SERVICE_UNAVAILABLE, message, reasonCode);
    }

    public static DmsException notFound(String message) {
        return new DmsException(HttpStatus.NOT_FOUND, message, null);
    }
}
