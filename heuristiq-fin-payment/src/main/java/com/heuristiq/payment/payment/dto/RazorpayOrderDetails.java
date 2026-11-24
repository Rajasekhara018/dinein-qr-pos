package com.heuristiq.paymentservice.payment.dto;

public record RazorpayOrderDetails(
        String orderId,
        long amountInPaise,
        String currency,
        String keyId
) {
}
