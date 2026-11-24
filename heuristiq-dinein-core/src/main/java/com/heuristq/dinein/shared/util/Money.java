package com.heuristq.dinein.shared.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Money helpers. All rupee amounts are {@link BigDecimal} with scale 2; Razorpay amounts are paise ({@code long}). */
public final class Money {

    public static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private Money() {
    }

    public static BigDecimal round(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    public static long toPaise(BigDecimal rupees) {
        return rupees.setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact();
    }

    public static BigDecimal fromPaise(long paise) {
        return BigDecimal.valueOf(paise).movePointLeft(2).setScale(2, RoundingMode.UNNECESSARY);
    }
}
