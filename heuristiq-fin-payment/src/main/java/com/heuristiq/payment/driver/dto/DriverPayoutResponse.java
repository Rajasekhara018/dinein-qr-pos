package com.heuristiq.paymentservice.driver.dto;

import com.heuristiq.paymentservice.shared.money.Money;
import com.heuristiq.paymentservice.driver.model.DriverPayout;

import java.time.LocalDateTime;
import java.math.BigDecimal;

public record DriverPayoutResponse(
        String id,
        String driverId,
        Money requestedAmount,
        String status,
        String utrNumber,
        LocalDateTime requestedAt,
        LocalDateTime processedAt
) {
    public static DriverPayoutResponse from(DriverPayout p) {
        return new DriverPayoutResponse(p.getId(), p.getDriverId(),
                Money.ofNullable(p.getRequestedAmount(), p.getRequestedAmountCurrency()),
                p.getStatus(), p.getUtrNumber(), p.getRequestedAt(), p.getProcessedAt());
    }
}
