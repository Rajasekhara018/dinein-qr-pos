package com.heuristiq.payment.payment.dto;

public record RazorpayOrderDetails(
        String orderId,
        long amountInPaise,
        String currency,
        String keyId
) {
}
