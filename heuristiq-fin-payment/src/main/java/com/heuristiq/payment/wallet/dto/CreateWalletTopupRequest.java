package com.heuristiq.paymentservice.wallet.dto;

import com.heuristiq.paymentservice.shared.money.PositiveMoney;
import com.heuristiq.paymentservice.shared.money.Money;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/**
 * @param gateway Optional — RAZORPAY / PAYU / TOUCAN / PINELABS (case-insensitive). Omitted/blank
 *                defaults to app.payment.default-gateway, same as CreatePaymentRequest.gateway.
 */
public record CreateWalletTopupRequest(
        @NotBlank String customerId,
        @NotNull @PositiveMoney Money amount,
        @NotBlank String idempotencyKey,
        String gateway,
        @jakarta.validation.constraints.Pattern(regexp = "[A-Z][A-Z0-9_]{0,99}") String appCode
) {
    /** Compatibility for installed clients and in-process callers without an origin app. */
    public CreateWalletTopupRequest(String customerId, Money amount, String idempotencyKey, String gateway) {
        this(customerId, amount, idempotencyKey, gateway, null);
    }
}
