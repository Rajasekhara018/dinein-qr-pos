package com.heuristiq.identity.pos.domain;

import static com.heuristiq.identity.pos.domain.PosConstants.*;

import com.heuristiq.identity.organization.legalprofile.domain.TaxId;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Normalises and validates legal entity and branch drafts against the rules of their country. */
public final class PosValidator {

    private PosValidator() {
    }

    /** The normalised draft (trimmed, codes upper-cased, defaults filled), or {@link PosException#invalid}. */
    public static LegalEntityDraft legalEntity(LegalEntityDraft draft) {
        if (draft == null) {
            throw PosException.invalid(ERROR_COUNTRY_UNSUPPORTED, FIELD_COUNTRY);
        }
        PosCountry country = PosCountry.of(draft.country())
                .orElseThrow(() -> PosException.invalid(ERROR_COUNTRY_UNSUPPORTED, FIELD_COUNTRY));
        String countryCode = country.name();

        String currency = trimToNull(draft.currency());
        if (currency != null && !currency.toUpperCase(Locale.ROOT).equals(country.currency())) {
            throw PosException.invalid(ERROR_CURRENCY_MISMATCH, FIELD_CURRENCY);
        }
        currency = country.currency();

        String legalName = trimToNull(draft.legalName());
        if (legalName == null || legalName.length() > MAX_LEGAL_NAME) {
            throw PosException.invalid(ERROR_LEGAL_NAME_INVALID, FIELD_LEGAL_NAME);
        }

        List<TaxId> taxIds = new ArrayList<>();
        for (int i = 0; i < draft.taxIds().size(); i++) {
            TaxId taxId = draft.taxIds().get(i);
            if (taxId == null || taxId.type() == null
                    || (taxId.type().country() != null && !taxId.type().country().equals(countryCode))
                    || !taxId.type().accepts(taxId.value())) {
                throw PosException.invalid(ERROR_TAX_ID_INVALID, FIELD_TAX_IDS + "[" + i + "]");
            }
            taxIds.add(taxId);
        }

        PosAddress address = address(draft.address(), country);

        String prefix = draft.invoicePrefix() == null ? "" : draft.invoicePrefix().trim().toUpperCase(Locale.ROOT);
        if (!INVOICE_PREFIX.matcher(prefix).matches()) {
            throw PosException.invalid(ERROR_INVOICE_PREFIX_INVALID, FIELD_INVOICE_PREFIX);
        }

        Integer month = draft.financialYearStartMonth();
        if (month == null) {
            month = country.defaultFinancialYearStartMonth();
        } else if (month < 1 || month > 12) {
            throw PosException.invalid(ERROR_FINANCIAL_YEAR_INVALID, FIELD_FINANCIAL_YEAR);
        }

        return new LegalEntityDraft(countryCode, legalName, currency, taxIds, address, prefix, month);
    }

    /** The normalised draft of a branch of {@code owner}, or {@link PosException#invalid}. */
    public static BranchDraft branch(BranchDraft draft, LegalEntity owner) {
        if (owner.status() != PosStatus.ACTIVE) {
            throw PosException.invalid(ERROR_LEGAL_ENTITY_INACTIVE, FIELD_LEGAL_ENTITY_ID);
        }
        PosCountry country = PosCountry.of(owner.country()).orElseThrow();

        String code = draft.code() == null ? "" : draft.code().trim().toUpperCase(Locale.ROOT);
        if (!BRANCH_CODE.matcher(code).matches()) {
            throw PosException.invalid(ERROR_BRANCH_CODE_INVALID, FIELD_CODE);
        }
        String name = trimToNull(draft.name());
        if (name == null || name.length() > MAX_BRANCH_NAME) {
            throw PosException.invalid(ERROR_BRANCH_NAME_INVALID, FIELD_NAME);
        }

        String timeZone = trimToNull(draft.timeZone());
        if (timeZone == null || !timeZone.contains("/")) {
            throw PosException.invalid(ERROR_TIME_ZONE_INVALID, FIELD_TIME_ZONE);
        }
        try {
            ZoneId.of(timeZone);
        } catch (DateTimeException e) {
            throw PosException.invalid(ERROR_TIME_ZONE_INVALID, FIELD_TIME_ZONE);
        }

        String localeTag = trimToNull(draft.locale());
        if (localeTag == null) {
            localeTag = country.defaultLocale();
        }
        Locale locale = Locale.forLanguageTag(localeTag);
        if (locale.getLanguage().isEmpty() || !country.name().equals(locale.getCountry())) {
            throw PosException.invalid(ERROR_LOCALE_MISMATCH, FIELD_LOCALE);
        }
        localeTag = locale.toLanguageTag();

        List<String> languages = new ArrayList<>();
        if (draft.languages().isEmpty()) {
            languages.add(locale.getLanguage());
        } else {
            for (int i = 0; i < draft.languages().size(); i++) {
                String language = draft.languages().get(i) == null ? "" : draft.languages().get(i).trim();
                if (!LANGUAGE.matcher(language).matches()) {
                    throw PosException.invalid(ERROR_LANGUAGE_INVALID, FIELD_LANGUAGES + "[" + i + "]");
                }
                languages.add(language);
            }
        }

        String phone = trimToNull(draft.phone());
        if (phone != null && !E164.matcher(phone).matches()) {
            throw PosException.invalid(ERROR_PHONE_INVALID, FIELD_PHONE);
        }
        String email = trimToNull(draft.email());
        if (email != null && !validEmail(email)) {
            throw PosException.invalid(ERROR_EMAIL_INVALID, FIELD_EMAIL);
        }

        return new BranchDraft(owner.id(), code, name, timeZone, localeTag, languages, phone, email, address(draft.address(), country));
    }

    private static boolean validEmail(String email) {
        int at = email.indexOf('@');
        return at > 0 && at == email.lastIndexOf('@') && at < email.length() - 1;
    }

    private static PosAddress address(PosAddress address, PosCountry country) {
        if (address == null) {
            throw addressError(FIELD_ADDRESS);
        }
        String line1 = trimToNull(address.line1());
        if (line1 == null || line1.length() > MAX_ADDRESS_LINE) {
            throw addressError(FIELD_LINE1);
        }
        String line2 = trimToNull(address.line2());
        if (line2 != null && line2.length() > MAX_ADDRESS_LINE) {
            throw addressError(FIELD_LINE2);
        }
        String city = trimToNull(address.city());
        if (city == null || city.length() > MAX_CITY) {
            throw addressError(FIELD_CITY);
        }
        String region = trimToNull(address.region());
        if ((region == null && country.regionRequired()) || (region != null && region.length() > MAX_REGION)) {
            throw addressError(FIELD_REGION);
        }
        String postalCode = trimToNull(address.postalCode());
        if (postalCode == null || !country.acceptsPostalCode(postalCode)) {
            throw addressError(FIELD_POSTAL_CODE);
        }
        String addressCountry = address.country() == null ? null : address.country().trim().toUpperCase(Locale.ROOT);
        if (!country.name().equals(addressCountry)) {
            throw addressError(FIELD_COUNTRY);
        }
        return new PosAddress(line1, line2, city, region, postalCode.toUpperCase(Locale.ROOT), country.name());
    }

    private static PosException addressError(String field) {
        return PosException.invalid(ERROR_ADDRESS_INVALID, FIELD_ADDRESS + "." + field);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
