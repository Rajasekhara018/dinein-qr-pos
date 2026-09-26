package com.heuristq.dinein.payment;

/** Raised inside the transaction when an order's payment needs manual attention (amount mismatch, paid twice). */
public record PaymentFlaggedEvent(Long orderId, String reason) {
}
