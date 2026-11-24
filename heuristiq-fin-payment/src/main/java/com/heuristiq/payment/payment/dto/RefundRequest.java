package com.heuristiq.paymentservice.payment.dto;

import com.heuristiq.paymentservice.shared.money.PositiveMoney;
import com.heuristiq.paymentservice.shared.money.Money;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import jakarta.validation.constraints.Digits;

/**
 * @param amount      null/omitted means a refund of the whole remaining refundable amount
 * @param reason      free-text reason, passed to the gateway
 * @param destination optional, {@code GATEWAY} (back to the original payment method) or {@code WALLET};
 *                    omitted means GATEWAY for gateway payments and WALLET for cash payments
 * @param reference   optional unique refund reference; repeating a request with the same reference returns
 *                    the existing refund instead of refunding again. Generated when omitted.
 */
public record RefundRequest(
        @PositiveMoney Money amount,
        String reason,
        String destination,
        @Size(max = 100) String reference
) {
    /** Contract of the original endpoint: amount and reason only. */
    public RefundRequest(Money amount, String reason) {
        this(amount, reason, null, null);
    }
}
