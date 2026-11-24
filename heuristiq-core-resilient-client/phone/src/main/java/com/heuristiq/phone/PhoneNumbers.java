package com.heuristiq.phone;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Parses, validates and normalises phone numbers from any country. The canonical form is E.164 ({@code +919876543210}):
 * store and compare numbers in it, so the same number typed as {@code 9876543210}, {@code 09876543210},
 * {@code +91 98765 43210} or {@code 919876543210} is one value.
 *
 * <p>A number written without a country code is read in the default region (the organization's home country): this
 * class's {@link #defaultRegion()} unless the caller passes one. Pure and stateless apart from the default-region lookup.
 */
public final class PhoneNumbers {

    /** System property naming the default region (ISO 3166 alpha-2, for example {@code IN}). */
    public static final String DEFAULT_REGION_PROPERTY = "railcab.phone.default-region";

    /** Environment variable naming the default region when the system property is absent. */
    public static final String DEFAULT_REGION_ENV = "PHONE_DEFAULT_REGION";

    /** Region used when nothing is configured: the platform started in India. */
    public static final String FALLBACK_REGION = "IN";

    private static final PhoneNumberUtil UTIL = PhoneNumberUtil.getInstance();

    private PhoneNumbers() {
    }

    /**
     * A parsed number.
     *
     * @param e164           canonical form with a leading plus, for example {@code +919876543210}
     * @param countryCode    calling code digits, for example {@code 91}
     * @param nationalNumber the number without the calling code, for example {@code 9876543210}
     * @param region         ISO region of the number, for example {@code IN}
     */
    public record Parsed(String e164, String countryCode, String nationalNumber, String region) {
    }

    /** The default region: system property, then environment variable, then {@link #FALLBACK_REGION}. */
    public static String defaultRegion() {
        String configured = System.getProperty(DEFAULT_REGION_PROPERTY);
        if (configured == null || configured.isBlank()) {
            configured = System.getenv(DEFAULT_REGION_ENV);
        }
        return configured == null || configured.isBlank() ? FALLBACK_REGION : configured.trim().toUpperCase(Locale.ROOT);
    }

    /** Parses a number written in the default region when it has no country code; empty when it is not a valid number. */
    public static Optional<Parsed> parse(String raw) {
        return parse(raw, defaultRegion());
    }

    /** Parses a number written in {@code region} when it has no country code; empty when it is not a valid number. */
    public static Optional<Parsed> parse(String raw, String region) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        try {
            Phonenumber.PhoneNumber number = UTIL.parse(raw.trim(), regionOrDefault(region));
            if (!UTIL.isValidNumber(number)) {
                return Optional.empty();
            }
            return Optional.of(new Parsed(
                    UTIL.format(number, PhoneNumberUtil.PhoneNumberFormat.E164),
                    String.valueOf(number.getCountryCode()),
                    UTIL.getNationalSignificantNumber(number),
                    UTIL.getRegionCodeForNumber(number)));
        } catch (NumberParseException e) {
            return Optional.empty();
        }
    }

    /** The canonical E.164 form, or empty when the text is not a valid number. */
    public static Optional<String> toE164(String raw) {
        return parse(raw).map(Parsed::e164);
    }

    /** The canonical E.164 form of a number written in {@code region}, or empty when it is not valid. */
    public static Optional<String> toE164(String raw, String region) {
        return parse(raw, region).map(Parsed::e164);
    }

    /** True when the text is a valid number (a country code, or the default region's national format). */
    public static boolean isValid(String raw) {
        return parse(raw).isPresent();
    }

    /** True when the text is a valid number, read in {@code region} when it has no country code. */
    public static boolean isValid(String raw, String region) {
        return parse(raw, region).isPresent();
    }

    /**
     * Every written form a stored number may have today, for looking up rows saved before the canonical form: the E.164
     * form and the digits without the plus (calling code included). The national number alone (how a bare Indian mobile was
     * saved) is added only when the number belongs to {@code region}, because a bare number carries no country: the national
     * digits of a number from another country must never match a row saved in the home region's bare form. The canonical form
     * comes first. Empty when the text is not a valid number.
     */
    public static List<String> lookupVariants(String raw, String region) {
        Optional<Parsed> parsed = parse(raw, region);
        if (parsed.isEmpty()) {
            return List.of();
        }
        Parsed p = parsed.get();
        Set<String> variants = new LinkedHashSet<>();
        variants.add(p.e164());
        variants.add(p.e164().substring(1));
        if (regionOrDefault(region).equals(p.region())) {
            variants.add(p.nationalNumber());
        }
        return List.copyOf(variants);
    }

    private static String regionOrDefault(String region) {
        return region == null || region.isBlank() ? defaultRegion() : region.trim().toUpperCase(Locale.ROOT);
    }
}
