package com.heuristiq.payment.shared.money;

import com.heuristiq.payment.shared.PaymentCommonConstants;
import java.math.BigDecimal;
import java.util.Locale;

/**
 * The currency written next to an amount (the {@code <column>_currency} columns of V24): set only where the amount is set, so
 * the two stay together as the tables' checks require.
 */
public final class AmountCurrency {

    private AmountCurrency() {
    }

    /** The currency of one amount: {@code currency} when the amount is set, otherwise none. */
    public static String of(BigDecimal amount, String currency) {
        return amount == null ? null : currency;
    }

    /**
     * The currency of one amount taken from a related row (for example a refund's payment): that row's currency when it is a
     * three-letter code, otherwise {@code fallback}; none when the amount is not set.
     */
    public static String of(BigDecimal amount, String relatedCurrency, String fallback) {
        if (amount == null) {
            return null;
        }
        return relatedCurrency != null && relatedCurrency.length() == PaymentCommonConstants.CURRENCY_CODE_LENGTH
                ? relatedCurrency.toUpperCase(Locale.ROOT) : fallback;
    }
}
