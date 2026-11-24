package com.heuristiq.uam.organization.legalprofile.domain;

import static com.heuristiq.uam.organization.legalprofile.domain.LegalProfileConstants.*;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Normalises and validates a legal profile as sent. Pure: no state, no I/O. Normalisation comes first (every text trimmed and
 * a blank one made null; codes upper-cased; tax id values upper-cased with all whitespace removed; postal codes upper-cased),
 * then the rules, so " in " is the country IN and "abcde 1234 f" a valid PAN. The first broken rule is refused with its
 * message key and the field's path.
 */
public final class LegalProfileValidator {

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private LegalProfileValidator() {
    }

    /** The validated profile; throws {@link LegalProfileException} of kind INVALID on the first broken rule. */
    public static LegalProfile validate(LegalProfileDraft draft) {
        LegalProfileDraft source = draft == null
                ? new LegalProfileDraft(null, null, null, null, null, null, null, null) : draft;
        LegalEntityType entityType = entityType(source.entityType());
        String legalName = limited(text(source.legalName()), MAX_LEGAL_NAME, FIELD_LEGAL_NAME);
        String tradingName = limited(text(source.tradingName()), MAX_TRADING_NAME, FIELD_TRADING_NAME);
        String registrationNumber = registrationNumber(source.registrationNumber());
        String registrationCountry = country(source.registrationCountry(), FIELD_REGISTRATION_COUNTRY);
        boolean taxRegistered = Boolean.TRUE.equals(source.taxRegistered());
        return new LegalProfile(entityType, legalName, tradingName, registrationNumber, registrationCountry, taxRegistered,
                taxIds(source.taxIds()), addresses(source.addresses()));
    }

    private static LegalEntityType entityType(String value) {
        String code = code(value);
        if (code == null) {
            return null;
        }
        try {
            return LegalEntityType.valueOf(code);
        } catch (IllegalArgumentException e) {
            throw LegalProfileException.invalid(ERROR_INVALID_ENTITY_TYPE, FIELD_ENTITY_TYPE);
        }
    }

    private static String registrationNumber(String value) {
        String number = limited(text(value), MAX_REGISTRATION_NUMBER, FIELD_REGISTRATION_NUMBER);
        if (number != null && !REGISTRATION_NUMBER.matcher(number).matches()) {
            throw LegalProfileException.invalid(ERROR_INVALID_REGISTRATION_NUMBER, FIELD_REGISTRATION_NUMBER);
        }
        return number;
    }

    private static List<TaxId> taxIds(List<LegalProfileDraft.TaxIdDraft> drafts) {
        if (drafts == null || drafts.isEmpty()) {
            return List.of();
        }
        if (drafts.size() > MAX_TAX_IDS) {
            throw LegalProfileException.invalid(ERROR_TOO_MANY_TAX_IDS, FIELD_TAX_IDS);
        }
        Set<TaxIdType> seen = EnumSet.noneOf(TaxIdType.class);
        List<TaxId> taxIds = new ArrayList<>();
        for (int i = 0; i < drafts.size(); i++) {
            LegalProfileDraft.TaxIdDraft draft = drafts.get(i);
            String path = FIELD_TAX_IDS + "[" + i + "].";
            TaxIdType type = taxType(draft == null ? null : draft.type(), path + FIELD_TYPE);
            if (!seen.add(type)) {
                throw LegalProfileException.invalid(ERROR_DUPLICATE_TAX_TYPE, path + FIELD_TYPE);
            }
            String value = draft.value() == null ? null : WHITESPACE.matcher(draft.value()).replaceAll("").toUpperCase(Locale.ROOT);
            if (!type.accepts(value)) {
                throw LegalProfileException.invalid(ERROR_INVALID_TAX_ID, path + FIELD_VALUE);
            }
            taxIds.add(new TaxId(type, value));
        }
        return taxIds;
    }

    private static TaxIdType taxType(String value, String path) {
        String code = code(value);
        if (code == null) {
            throw LegalProfileException.invalid(ERROR_INVALID_TAX_TYPE, path);
        }
        try {
            return TaxIdType.valueOf(code);
        } catch (IllegalArgumentException e) {
            throw LegalProfileException.invalid(ERROR_INVALID_TAX_TYPE, path);
        }
    }

    private static List<LegalAddress> addresses(List<LegalProfileDraft.AddressDraft> drafts) {
        if (drafts == null || drafts.isEmpty()) {
            return List.of();
        }
        Set<AddressKind> seen = EnumSet.noneOf(AddressKind.class);
        List<LegalAddress> addresses = new ArrayList<>();
        for (int i = 0; i < drafts.size(); i++) {
            LegalProfileDraft.AddressDraft draft = drafts.get(i);
            String path = FIELD_ADDRESSES + "[" + i + "].";
            AddressKind kind = addressKind(draft == null ? null : draft.kind(), path + FIELD_KIND);
            // Two kinds, each at most once: a third address is always a repeated kind, so this also caps the list at two.
            if (!seen.add(kind)) {
                throw LegalProfileException.invalid(ERROR_DUPLICATE_ADDRESS_KIND, path + FIELD_KIND);
            }
            addresses.add(address(kind, draft, path));
        }
        return addresses;
    }

    private static AddressKind addressKind(String value, String path) {
        String code = code(value);
        if (code == null) {
            throw LegalProfileException.invalid(ERROR_INVALID_ADDRESS_KIND, path);
        }
        try {
            return AddressKind.valueOf(code);
        } catch (IllegalArgumentException e) {
            throw LegalProfileException.invalid(ERROR_INVALID_ADDRESS_KIND, path);
        }
    }

    private static LegalAddress address(AddressKind kind, LegalProfileDraft.AddressDraft draft, String path) {
        String line1 = required(limited(text(draft.line1()), MAX_ADDRESS_LINE, path + FIELD_LINE1), path + FIELD_LINE1);
        String line2 = limited(text(draft.line2()), MAX_ADDRESS_LINE, path + FIELD_LINE2);
        String city = required(limited(text(draft.city()), MAX_CITY, path + FIELD_CITY), path + FIELD_CITY);
        String country = required(country(draft.country(), path + FIELD_COUNTRY), path + FIELD_COUNTRY);
        String region = limited(text(draft.region()), MAX_REGION, path + FIELD_REGION);
        if (region == null && REGION_REQUIRED_COUNTRIES.contains(country)) {
            throw LegalProfileException.invalid(ERROR_ADDRESS_REQUIRED, path + FIELD_REGION);
        }
        String postalCode = postalCode(draft.postalCode(), country, path + FIELD_POSTAL_CODE);
        return new LegalAddress(kind, line1, line2, city, region, postalCode, country);
    }

    private static String postalCode(String value, String country, String path) {
        String postalCode = limited(code(value), MAX_POSTAL_CODE, path);
        if (NO_POSTAL_CODE_COUNTRIES.contains(country)) {
            if (postalCode != null) {
                throw LegalProfileException.invalid(ERROR_INVALID_POSTAL_CODE, path);
            }
            return null;
        }
        Pattern required = REQUIRED_POSTAL_CODES.get(country);
        if (required != null) {
            if (postalCode == null || !required.matcher(postalCode).matches()) {
                throw LegalProfileException.invalid(ERROR_INVALID_POSTAL_CODE, path);
            }
            return postalCode;
        }
        if (postalCode != null && !OTHER_POSTAL_CODE.matcher(postalCode).matches()) {
            throw LegalProfileException.invalid(ERROR_INVALID_POSTAL_CODE, path);
        }
        return postalCode;
    }

    private static String country(String value, String path) {
        String code = code(value);
        if (code != null && !ISO_COUNTRIES.contains(code)) {
            throw LegalProfileException.invalid(ERROR_INVALID_COUNTRY, path);
        }
        return code;
    }

    private static String required(String value, String path) {
        if (value == null) {
            throw LegalProfileException.invalid(ERROR_ADDRESS_REQUIRED, path);
        }
        return value;
    }

    private static String limited(String value, int max, String path) {
        if (value != null && value.length() > max) {
            throw LegalProfileException.invalid(ERROR_TOO_LONG, path);
        }
        return value;
    }

    private static String text(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String code(String value) {
        String trimmed = text(value);
        return trimmed == null ? null : trimmed.toUpperCase(Locale.ROOT);
    }
}
