package com.heuristiq.paymentservice.shared.exception;

/**
 * The gateway refund call ended without a definite answer (timeout, connection failure, gateway 5xx),
 * so the refund may or may not have happened. The refund stays PROCESSING and is resolved by repeating
 * the request with the same refund reference; the gateway call is idempotent on that reference.
 */
public class RefundOutcomeUnknownException extends PaymentServiceException {
    public RefundOutcomeUnknownException(String message) {
        super(message);
    }
}
