package com.heuristiq.uam.helper;

import com.heuristiq.phone.PhoneNumbers;
import com.heuristiq.uam.common.UamException;

import java.util.Optional;

/**
 * Single place that decides how a phone number is stored on an account and when a stored number is "the same person"
 * as a number typed by a client, for any country.
 *
 * <p>New accounts store the parsed form: {@code countryCode} = calling code digits (for example {@code 91}) and
 * {@code number} = national significant number. Rows created before international support hold a bare 10-digit Indian
 * number with a blank, {@code +91} or {@code 91} country code; they are never rewritten, so a lookup must find both
 * shapes. Matching rule: the national number is equal AND the country is compatible, meaning the stored calling code
 * equals the parsed one, or the stored code is blank (legacy) and the parsed number belongs to the default region.</p>
 *
 * <p>Stateless and pure; holds no business rules beyond the matching rule above.</p>
 */
public final class PhoneIdentity {


    private PhoneIdentity() {
    }

    /**
     * What a lookup searches for.
     *
     * @param e164              canonical E.164 form ({@code null} for the unparsable fallback)
     * @param nationalNumber    the value the {@code number} column must equal
     * @param countryCode       calling code digits, or {@code null} for the unparsable fallback
     * @param legacyCompatible  whether a stored row with a blank country code can be the same number
     */
    public record Lookup(String e164, String nationalNumber, String countryCode, boolean legacyCompatible) {
    }

    /**
     * Reads {@code raw} (E.164, or national form in the default region) into a lookup. A value that is not a valid phone
     * number falls back to an exact match against legacy rows (blank country code), so odd historic rows stay reachable
     * and arbitrary login identifiers never match a foreign-country row. Empty only for blank input.
     */
    public static Optional<Lookup> lookup(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(PhoneNumbers.parse(raw)
                .map(p -> new Lookup(p.e164(), p.nationalNumber(), p.countryCode(),
                        p.region().equalsIgnoreCase(PhoneNumbers.defaultRegion())))
                .orElseGet(() -> new Lookup(null, raw.trim(), null, true)));
    }

    /** True when {@code stored} is the same real number the lookup describes (see the class rule). */
    public static boolean matches(RPhoneNumber stored, Lookup lookup) {
        if (stored == null || lookup == null || stored.getNumber() == null) {
            return false;
        }
        String number = stored.getNumber().trim();
        String storedCode = stored.getNormalizedCountryCode();
        if (lookup.countryCode() == null) {
            return storedCode.isEmpty() && number.equals(lookup.nationalNumber());
        }
        if (!number.equals(lookup.nationalNumber())) {
            return false;
        }
        return storedCode.isEmpty() ? lookup.legacyCompatible() : storedCode.equals(lookup.countryCode());
    }

    /** Convenience for repository adapters: does {@code stored} match the phone typed as {@code raw}? */
    public static boolean matches(RPhoneNumber stored, String raw) {
        return lookup(raw).map(l -> matches(stored, l)).orElse(false);
    }

    /**
     * The form to store for a new or changed number.
     *
     * @throws UamException 400 when the text is not a valid phone number
     */
    public static RPhoneNumber toStored(String raw) {
        PhoneNumbers.Parsed parsed = parseOrThrow(raw);
        return new RPhoneNumber(parsed.countryCode(), parsed.nationalNumber());
    }

    /**
     * The E.164 form to send to a delivery channel (SMS/WhatsApp) or use as a canonical key.
     *
     * @throws UamException 400 when the text is not a valid phone number
     */
    public static String toE164(String raw) {
        return parseOrThrow(raw).e164();
    }

    /**
     * Canonical E.164 value stored in the unique {@code phoneE164} column/field: one value per real number. A parsable
     * number gives its E.164 form; a historic row that does not parse follows the same digit rules as the Flyway
     * backfill ({@link #legacyE164(String, String)}). {@code null} when there is no usable number.
     */
    public static String e164Of(RPhoneNumber stored) {
        if (stored == null || !stored.hasValue()) {
            return null;
        }
        String code = stored.getNormalizedCountryCode();
        String number = stored.getNormalizedNumber();
        String candidate = code.isEmpty() ? number : "+" + code + number;
        return PhoneNumbers.toE164(candidate).orElseGet(() -> legacyE164(code, number));
    }

    /**
     * The pure digit rule the Postgres backfill migration applies to rows that existed before the canonical column:
     * a blank or {@code 91} country code with exactly 10 digits (or any digits under {@code 91}) becomes
     * {@code +91<number>}; another country code becomes {@code +<code><number>}; otherwise {@code null}.
     *
     * @param countryCode digits of the stored country code (may be empty)
     * @param number      digits of the stored number (may be empty)
     */
    public static String legacyE164(String countryCode, String number) {
        String code = countryCode == null ? "" : countryCode.replaceAll("[^0-9]", "");
        String digits = number == null ? "" : number.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) {
            return null;
        }
        if (code.isEmpty()) {
            return digits.length() == PhoneConstants.LEGACY_INDIAN_LENGTH ? PhoneConstants.LEGACY_INDIAN_PREFIX + digits : null;
        }
        return "+" + code + digits;
    }

    /**
     * The text a user search matches against the canonical column: the E.164 form when the term is a whole valid number,
     * else the term stripped to an optional plus and digits (so a leading plus or a country code still finds the
     * account). Empty when the term has fewer than 3 digits or other characters.
     */
    public static Optional<String> searchFragment(String term) {
        if (term == null) {
            return Optional.empty();
        }
        Optional<String> whole = PhoneNumbers.toE164(term);
        if (whole.isPresent()) {
            return whole;
        }
        String stripped = term.replaceAll("[\\s().-]", "");
        return stripped.matches("\\+?[0-9]{3,}") ? Optional.of(stripped) : Optional.empty();
    }

    /**
     * Stable key/delivery form of a client-typed number: E.164 when it parses (so {@code 9876543210} and
     * {@code +919876543210} give the same value), otherwise the trimmed text unchanged.
     */
    public static String canonical(String raw) {
        return PhoneNumbers.toE164(raw).orElseGet(() -> raw == null ? null : raw.trim());
    }

    private static PhoneNumbers.Parsed parseOrThrow(String raw) {
        return PhoneNumbers.parse(raw).orElseThrow(() -> new UamException("Phone number " + PhoneConstants.INVALID_PHONE_MESSAGE));
    }
}
