package com.heuristiq.payment.payment.gateway.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Reads the amount a gateway reports in its status response into an exact rupee amount. A missing or unreadable value is
 * null, which {@link com.heuristiq.payment.payment.service.GatewayAmountVerifier} treats as a mismatch (fails closed).
 */
public final class GatewayAmountParser {

    /** Decimal places of a rupee amount (paise). */
    private static final int SUBUNIT_DIGITS = 2;

    private GatewayAmountParser() {
    }

    /** An amount the gateway gives in rupees ("150.00"); null when blank or not a number. */
    public static BigDecimal rupees(String value) {
        BigDecimal amount = parse(value);
        return amount == null ? null : amount.setScale(SUBUNIT_DIGITS, RoundingMode.HALF_UP);
    }

    /** An amount the gateway gives in paise ("15000" is 150.00 rupees); null when blank or not a number. */
    public static BigDecimal fromSubunits(String value) {
        BigDecimal amount = parse(value);
        return amount == null ? null : amount.movePointLeft(SUBUNIT_DIGITS).setScale(SUBUNIT_DIGITS, RoundingMode.HALF_UP);
    }

    private static BigDecimal parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
