package com.heuristiq.identity.pos.domain;

import java.util.regex.Pattern;

/** Fixed values of the POS organization model: limits, patterns, field names and the message keys the web app translates. */
public final class PosConstants {

    /** Length limits (characters, after trimming). */
    public static final int MAX_LEGAL_NAME = 200;
    public static final int MAX_BRANCH_NAME = 120;
    public static final int MAX_ADDRESS_LINE = 200;
    public static final int MAX_CITY = 100;
    public static final int MAX_REGION = 100;

    /** Postal code patterns by country, checked after trim and upper-casing. */
    public static final Pattern POSTAL_IN = Pattern.compile("^[1-9][0-9]{5}$");
    public static final Pattern POSTAL_DE = Pattern.compile("^[0-9]{5}$");
    public static final Pattern POSTAL_FR = Pattern.compile("^[0-9]{5}$");
    public static final Pattern POSTAL_GB = Pattern.compile("^[A-Z]{1,2}[0-9][A-Z0-9]? ?[0-9][A-Z]{2}$");
    public static final Pattern POSTAL_US = Pattern.compile("^[0-9]{5}(-[0-9]{4})?$");
    public static final Pattern POSTAL_AU = Pattern.compile("^[0-9]{4}$");

    public static final Pattern INVOICE_PREFIX = Pattern.compile("^[A-Z0-9-]{1,10}$");
    public static final Pattern BRANCH_CODE = Pattern.compile("^[A-Z0-9-]{2,20}$");
    public static final Pattern LANGUAGE = Pattern.compile("^[a-z]{2}$");
    public static final Pattern E164 = Pattern.compile("^\\+[1-9][0-9]{6,14}$");

    /** Message keys (the web app translates them, so they must never change). */
    public static final String ERROR_COUNTRY_UNSUPPORTED = "pos.country.unsupported";
    public static final String ERROR_CURRENCY_MISMATCH = "pos.currency.mismatch";
    public static final String ERROR_LEGAL_NAME_INVALID = "pos.legalName.invalid";
    public static final String ERROR_TAX_ID_INVALID = "pos.taxId.invalid";
    public static final String ERROR_ADDRESS_INVALID = "pos.address.invalid";
    public static final String ERROR_INVOICE_PREFIX_INVALID = "pos.invoicePrefix.invalid";
    public static final String ERROR_FINANCIAL_YEAR_INVALID = "pos.financialYearStartMonth.invalid";
    public static final String ERROR_LEGAL_ENTITY_INACTIVE = "pos.legalEntity.inactive";
    public static final String ERROR_BRANCH_CODE_INVALID = "pos.branch.code.invalid";
    public static final String ERROR_BRANCH_NAME_INVALID = "pos.branch.name.invalid";
    public static final String ERROR_TIME_ZONE_INVALID = "pos.timeZone.invalid";
    public static final String ERROR_LOCALE_MISMATCH = "pos.locale.mismatch";
    public static final String ERROR_LANGUAGE_INVALID = "pos.language.invalid";
    public static final String ERROR_PHONE_INVALID = "pos.phone.invalid";
    public static final String ERROR_EMAIL_INVALID = "pos.email.invalid";
    public static final String ERROR_ORGANIZATION_NOT_FOUND = "pos.organization.notFound";
    public static final String ERROR_LEGAL_ENTITY_NOT_FOUND = "pos.legalEntity.notFound";
    public static final String ERROR_BRANCH_NOT_FOUND = "pos.branch.notFound";
    public static final String ERROR_COUNTRY_LOCKED = "pos.legalEntity.countryLocked";
    public static final String ERROR_BRANCH_LEGAL_ENTITY_LOCKED = "pos.branch.legalEntityLocked";
    public static final String ERROR_BRANCH_CODE_TAKEN = "pos.branch.codeTaken";
    public static final String ERROR_STALE_VERSION = "pos.staleVersion";
    public static final String ERROR_STAFF_NOT_MEMBER = "pos.staff.notMember";

    /** Field names, used as the field path of a refusal. */
    public static final String FIELD_COUNTRY = "country";
    public static final String FIELD_CURRENCY = "currency";
    public static final String FIELD_LEGAL_NAME = "legalName";
    public static final String FIELD_TAX_IDS = "taxIds";
    public static final String FIELD_ADDRESS = "address";
    public static final String FIELD_LINE1 = "line1";
    public static final String FIELD_LINE2 = "line2";
    public static final String FIELD_CITY = "city";
    public static final String FIELD_REGION = "region";
    public static final String FIELD_POSTAL_CODE = "postalCode";
    public static final String FIELD_INVOICE_PREFIX = "invoicePrefix";
    public static final String FIELD_FINANCIAL_YEAR = "financialYearStartMonth";
    public static final String FIELD_LEGAL_ENTITY_ID = "legalEntityId";
    public static final String FIELD_CODE = "code";
    public static final String FIELD_NAME = "name";
    public static final String FIELD_TIME_ZONE = "timeZone";
    public static final String FIELD_LOCALE = "locale";
    public static final String FIELD_LANGUAGES = "languages";
    public static final String FIELD_PHONE = "phone";
    public static final String FIELD_EMAIL = "email";
    public static final String FIELD_VERSION = "version";
    public static final String FIELD_USER_IDS = "userIds";

    private PosConstants() {
    }
}
