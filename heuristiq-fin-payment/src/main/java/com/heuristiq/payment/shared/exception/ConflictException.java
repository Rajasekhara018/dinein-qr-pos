package com.heuristiq.payment.shared.exception;

/** A request that is well formed but conflicts with the current state of the resource (HTTP 409). */
public class ConflictException extends PaymentServiceException {
    public ConflictException(String message) {
        super(message);
    }
}
