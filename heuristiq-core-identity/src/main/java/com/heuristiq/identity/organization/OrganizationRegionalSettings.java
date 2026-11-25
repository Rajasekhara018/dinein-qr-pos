package com.heuristiq.identity.organization;

import com.heuristiq.identity.common.UamException;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.Currency;
import java.util.Locale;
import java.util.Set;

/**
 * Checks and normalizes an organization's regional settings: the currency its amounts are in (ISO 4217), its home country
 * (ISO 3166 alpha-2), its time zone (IANA) and the language its people start in. A blank value means "not set"; anything else
 * must be a real code, so a typo is refused when it is saved, not discovered on the first booking.
 */
final class OrganizationRegionalSettings {

    private static final Set<String> COUNTRIES = Set.of(Locale.getISOCountries());

    private OrganizationRegionalSettings() {
    }

    /** INR, USD ...; null when blank. */
    static String currency(String value) {
        String code = blankToNull(value);
        if (code == null) {
            return null;
        }
        String upper = code.toUpperCase(Locale.ROOT);
        try {
            return Currency.getInstance(upper).getCurrencyCode();
        } catch (IllegalArgumentException e) {
            throw new UamException("currency: " + code + " is not an ISO 4217 currency code");
        }
    }

    /** IN, AE ...; null when blank. */
    static String country(String value) {
        String code = blankToNull(value);
        if (code == null) {
            return null;
        }
        String upper = code.toUpperCase(Locale.ROOT);
        if (!COUNTRIES.contains(upper)) {
            throw new UamException("country: " + code + " is not an ISO 3166 country code");
        }
        return upper;
    }

    /** Asia/Kolkata ...; null when blank. Offsets such as +05:30 are refused: a region keeps daylight-saving rules right. */
    static String timeZone(String value) {
        String zone = blankToNull(value);
        if (zone == null) {
            return null;
        }
        if (!ZoneId.getAvailableZoneIds().contains(zone)) {
            throw new UamException("timeZone: " + zone + " is not an IANA time zone (for example Asia/Kolkata)");
        }
        try {
            return ZoneId.of(zone).getId();
        } catch (DateTimeException e) {
            throw new UamException("timeZone: " + zone + " is not an IANA time zone (for example Asia/Kolkata)");
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
