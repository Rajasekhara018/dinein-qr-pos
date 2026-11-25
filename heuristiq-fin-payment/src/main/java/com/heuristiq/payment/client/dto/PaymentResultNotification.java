package com.heuristiq.payment.client.dto;

import com.heuristiq.payment.shared.money.Money;
import java.math.BigDecimal;

/**
 * Body for POST /rides/{rideId}/payment-result — notifies the Ride service once a
 * payment reaches a terminal outcome (CAPTURED/SETTLED -> COMPLETED, or FAILED).
 */
public record PaymentResultNotification(
        String status,
        String paymentId,
        Money finalAmount
) {
    public static final String COMPLETED = "COMPLETED";
    public static final String FAILED = "FAILED";
}
