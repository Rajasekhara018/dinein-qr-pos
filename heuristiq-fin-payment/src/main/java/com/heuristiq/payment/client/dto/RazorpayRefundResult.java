package com.heuristiq.payment.client.dto;

/** Minimal parse of Razorpay's refund-object response — just enough to log/confirm success. */
public record RazorpayRefundResult(String refundId, String status) {
}
