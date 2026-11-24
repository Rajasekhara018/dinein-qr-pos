package com.heuristiq.paymentservice.payment.dto;

import com.heuristiq.paymentservice.shared.money.PositiveMoney;
import com.heuristiq.paymentservice.shared.money.Money;
import com.heuristiq.phone.validation.ValidPhone;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import jakarta.validation.constraints.Digits;

/**
 * @param gateway Optional — RAZORPAY / PAYU / TOUCAN / PINELABS (case-insensitive). Only
 *                meaningful when {@code method} is NOT "CASH". Omitted/blank defaults to RAZORPAY
 *                so existing callers (Ride/Dispatch services) that only ever sent
 *                {@code method: RAZORPAY} keep working completely unchanged. See
 *                Payment.gateway's javadoc for the full method-vs-gateway relationship.
 * @param testTransaction Optional, defaults to false. When true, a rideId that doesn't exist in
 *                Rider_RideService (e.g. a scratch/test id used to exercise a gateway's checkout
 *                flow) no longer fails the payment with "Ride not found" — see
 *                PaymentService#createPayment.
 * @param customerName   Optional payer details entered at checkout, shown on the receipt/invoice.
 * @param customerEmail  Optional, see {@code customerName}.
 * @param customerMobile Optional, see {@code customerName}.
 */
public record CreatePaymentRequest(
        String rideId,
        @NotBlank String customerId,
        @NotNull @PositiveMoney Money amount,
        @NotBlank String method,
        @NotBlank String idempotencyKey,
        String gateway,
        Boolean testTransaction,
        String customerName,
        String customerEmail,
        @ValidPhone String customerMobile,
        @Size(max = 64) String sourceService,
        @Size(max = 128) String sourceReference
) {

    /** A ride payment (no external source), as before. */
    public CreatePaymentRequest(String rideId, String customerId, Money amount, String method, String idempotencyKey,
                                String gateway, Boolean testTransaction, String customerName, String customerEmail,
                                String customerMobile) {
        this(rideId, customerId, amount, method, idempotencyKey, gateway, testTransaction, customerName, customerEmail,
                customerMobile, null, null);
    }

    /** True when another Rider application raised this payment instead of a ride. */
    public boolean isExternal() {
        return sourceService != null && !sourceService.isBlank();
    }

}
