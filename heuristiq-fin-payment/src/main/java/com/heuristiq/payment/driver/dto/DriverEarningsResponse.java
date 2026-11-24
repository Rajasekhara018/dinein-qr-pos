package com.heuristiq.paymentservice.driver.dto;

import com.heuristiq.paymentservice.shared.money.Money;
import com.heuristiq.paymentservice.driver.model.DriverPayment;
import com.heuristiq.paymentservice.driver.model.DriverSplitStatus;

import java.time.LocalDateTime;
import java.math.BigDecimal;

/**
 * One ride's driver payment record. {@code splitStatus}: {@code SPLIT} when {@code platformFee}/{@code netAmount} are the ride's
 * own fare split (the central commission plan); {@code PENDING_SPLIT} while that split is not known yet, and
 * {@code SPLIT_MISMATCH} when it was for another fare: in both cases {@code platformFeePercent}, {@code platformFee} and
 * {@code netAmount} are null (never an estimate).
 */
public record DriverEarningsResponse(
        String id,
        String driverId,
        String rideId,
        Money amount,
        BigDecimal platformFeePercent,
        Money platformFee,
        Money netAmount,
        DriverSplitStatus splitStatus,
        LocalDateTime createdAt
) {
    public static DriverEarningsResponse from(DriverPayment p) {
        return new DriverEarningsResponse(p.getId(), p.getDriverId(), p.getRideId(),
                Money.ofNullable(p.getAmount(), p.getAmountCurrency()), p.getPlatformFeePercent(),
                Money.ofNullable(p.getPlatformFee(), p.getPlatformFeeCurrency()),
                Money.ofNullable(p.getNetAmount(), p.getNetAmountCurrency()),
                p.getSplitStatus() == null ? DriverSplitStatus.SPLIT : p.getSplitStatus(), p.getCreatedAt());
    }
}
