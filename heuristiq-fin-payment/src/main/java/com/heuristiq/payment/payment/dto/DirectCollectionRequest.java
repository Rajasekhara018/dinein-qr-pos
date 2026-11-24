package com.heuristiq.paymentservice.payment.dto;

import com.heuristiq.paymentservice.payment.PaymentConstants;
import com.heuristiq.paymentservice.shared.money.Money;
import com.heuristiq.paymentservice.shared.money.PositiveMoney;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /api/v1/internal/payments/direct-collections} (Rider_RideService only, service token): a ride fare the rider
 * paid to the driver directly. The organization comes from the {@code X-Tenant-Id} header, never from the body.
 *
 * @param rideId     the ride; one record per ride (the idempotency key)
 * @param customerId the rider who paid
 * @param driverId   the driver who collected it
 * @param amount     what was collected, {@code {code, value}} in the deployment currency
 * @param channel    {@code CASH} or {@code UPI} (to the driver's own UPI id)
 */
public record DirectCollectionRequest(
        @NotBlank @Size(max = PaymentConstants.MAX_ID) String rideId,
        @Size(max = PaymentConstants.MAX_ID) String customerId,
        @NotBlank @Size(max = PaymentConstants.MAX_ID) String driverId,
        @NotNull @PositiveMoney Money amount,
        @NotBlank @Pattern(regexp = PaymentConstants.DIRECT_COLLECTION_CHANNEL_PATTERN,
                message = PaymentConstants.DIRECT_COLLECTION_CHANNEL_MESSAGE) String channel) {
}
