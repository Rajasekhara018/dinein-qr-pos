package com.heuristq.dinein.payment;

/** Raised when a refund could not be started or the provider reported it as failed. */
public record RefundFailedEvent(Long orderId, String provider) {
}
