package com.heuristiq.paymentservice.shared.money;

import java.util.Currency;
import java.util.Locale;

/**
 * The deployment's currency for an amount that arrives in the legacy shape (a bare number), read by
 * {@link MoneyJsonDeserializer}. Jackson creates deserializers itself, so the configured currency is published here once
 * at startup by the service's money settings; until then (and in plain unit tests) it is {@link #FALLBACK_CURRENCY}.
 */
public final class MoneyDefaults {

    /** The currency used before the settings are loaded. */
    public static final String FALLBACK_CURRENCY = "INR";

    private static volatile String currency = FALLBACK_CURRENCY;

    private MoneyDefaults() {
    }

    /** @return the currency a legacy bare amount is read in */
    public static String currency() {
        return currency;
    }

    /**
     * Publishes the configured currency.
     *
     * @param code an ISO 4217 code
     * @throws IllegalArgumentException when it is not one
     */
    public static void useCurrency(String code) {
        currency = Currency.getInstance(code.trim().toUpperCase(Locale.ROOT)).getCurrencyCode();
    }
}
