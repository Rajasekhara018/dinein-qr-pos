package com.heuristiq.payment.payment.service;

import com.heuristiq.payment.config.MoneyProperties;
import com.heuristiq.payment.payment.model.Payment;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Compares what a gateway reports as paid (amount in paise and currency) with what the payment row
 * expects, in every path that can capture a payment. Fails closed: a missing amount or currency is a
 * mismatch. Only ids and amounts are logged.
 */
@Slf4j
@Component
public class GatewayAmountVerifier {

    private final MoneyProperties moneyProperties;

    /** @param moneyProperties the currency this deployment takes payments in (app.payment.currency) */
    public GatewayAmountVerifier(MoneyProperties moneyProperties) {
        this.moneyProperties = moneyProperties;
    }

    /** @return the currency every payment and top-up is expected in (app.payment.currency) */
    public String currency() {
        return moneyProperties.getCurrency();
    }

    /**
     * @param reportedPaise    amount the gateway reports, in paise; null when absent
     * @param reportedCurrency currency the gateway reports; null when absent
     * @return true only when the reported amount and currency match {@code payment}
     */
    public boolean matches(Payment payment, Long reportedPaise, String reportedCurrency) {
        return matches(payment.getInternalPaymentId(), payment.getAmount(), payment.getCurrency(),
                reportedPaise, reportedCurrency);
    }

    /** Same check for records that are not {@link Payment}s, such as wallet top-up sessions. */
    public boolean matches(String id, BigDecimal expectedAmount, String expectedCurrency,
                           Long reportedPaise, String reportedCurrency) {
        String configured = currency();
        String currency = expectedCurrency == null || expectedCurrency.isBlank() ? configured : expectedCurrency;
        boolean ok = expectedAmount != null && reportedPaise != null && reportedCurrency != null
                && toPaise(expectedAmount) == reportedPaise.longValue()
                && currency.equalsIgnoreCase(reportedCurrency)
                && configured.equalsIgnoreCase(reportedCurrency);
        if (!ok) {
            log.error("AMOUNT_MISMATCH for {}: expectedPaise={} reportedPaise={} expectedCurrency={} reportedCurrency={}",
                    id, expectedAmount == null ? null : toPaise(expectedAmount), reportedPaise, currency, reportedCurrency);
        }
        return ok;
    }

    /** Converts an exact rupee amount to paise, rounding half up. */
    public static long toPaise(BigDecimal rupees) {
        return rupees.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValue();
    }

    /** Converts a rupee amount reported by a gateway adapter to paise; null stays null. */
    public static Long toPaiseOrNull(BigDecimal rupees) {
        return rupees == null ? null : toPaise(rupees);
    }
}
