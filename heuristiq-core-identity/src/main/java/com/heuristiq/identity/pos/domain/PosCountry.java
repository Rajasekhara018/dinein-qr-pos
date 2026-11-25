package com.heuristiq.identity.pos.domain;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/** The countries the POS supports, with the currency, locale, financial year and postal-code rules of each. */
public enum PosCountry {
    IN("INR", "en-IN", 4, true, PosConstants.POSTAL_IN),
    DE("EUR", "de-DE", 1, false, PosConstants.POSTAL_DE),
    GB("GBP", "en-GB", 4, false, PosConstants.POSTAL_GB),
    FR("EUR", "fr-FR", 1, false, PosConstants.POSTAL_FR),
    US("USD", "en-US", 1, true, PosConstants.POSTAL_US),
    AU("AUD", "en-AU", 7, true, PosConstants.POSTAL_AU);

    private final String currency;
    private final String defaultLocale;
    private final int defaultFinancialYearStartMonth;
    private final boolean regionRequired;
    private final Pattern postalCode;

    PosCountry(String currency, String defaultLocale, int defaultFinancialYearStartMonth, boolean regionRequired,
            Pattern postalCode) {
        this.currency = currency;
        this.defaultLocale = defaultLocale;
        this.defaultFinancialYearStartMonth = defaultFinancialYearStartMonth;
        this.regionRequired = regionRequired;
        this.postalCode = postalCode;
    }

    /** ISO 4217 currency of the country. */
    public String currency() {
        return currency;
    }

    /** BCP 47 locale a branch gets when none is given. */
    public String defaultLocale() {
        return defaultLocale;
    }

    /** Month (1-12) the financial year starts in. */
    public int defaultFinancialYearStartMonth() {
        return defaultFinancialYearStartMonth;
    }

    /** True when addresses must name a region (state). */
    public boolean regionRequired() {
        return regionRequired;
    }

    /** True when the postal code (trimmed, upper-cased) is well-formed for this country. */
    public boolean acceptsPostalCode(String value) {
        return value != null && postalCode.matcher(value.trim().toUpperCase(Locale.ROOT)).matches();
    }

    /** The supported country with this ISO code (case-insensitive), or empty. */
    public static Optional<PosCountry> of(String isoCode) {
        if (isoCode == null) {
            return Optional.empty();
        }
        String code = isoCode.trim().toUpperCase(Locale.ROOT);
        for (PosCountry country : values()) {
            if (country.name().equals(code)) {
                return Optional.of(country);
            }
        }
        return Optional.empty();
    }
}
