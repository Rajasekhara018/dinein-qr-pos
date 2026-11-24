package com.heuristiq.paymentservice.client.dto;

import com.heuristiq.paymentservice.shared.money.Money;
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
