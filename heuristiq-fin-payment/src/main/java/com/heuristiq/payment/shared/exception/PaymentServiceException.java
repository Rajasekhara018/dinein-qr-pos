package com.heuristiq.paymentservice.shared.exception;

/** General-purpose business-rule violation — mirrors the monolith's BookingException. */
public class PaymentServiceException extends RuntimeException {
    public PaymentServiceException(String message) {
        super(message);
    }
}
