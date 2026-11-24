package com.heuristiq.paymentservice.client.dto;

/** Minimal parse of Razorpay's refund-object response — just enough to log/confirm success. */
public record RazorpayRefundResult(String refundId, String status) {
}
