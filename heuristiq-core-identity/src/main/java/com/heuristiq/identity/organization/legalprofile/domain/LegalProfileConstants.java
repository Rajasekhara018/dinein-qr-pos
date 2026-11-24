package com.heuristiq.uam.organization.legalprofile.domain;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Fixed values of an organization's legal profile: length limits, value patterns, postal-code rules by country, the message
 * keys sent to the web app (it translates them, so they must never change) and the audit names. The same rules are used by
 * the franchise legal profile, so one web component edits both.
 */
public final class LegalProfileConstants {

    /** Length limits (characters, after trimming). */
    public static final int MAX_LEGAL_NAME = 200;
    public static final int MAX_TRADING_NAME = 200;
    public static final int MAX_REGISTRATION_NUMBER = 50;
    public static final int MAX_ADDRESS_LINE = 200;
    public static final int MAX_CITY = 100;
    public static final int MAX_REGION = 100;
    public static final int MAX_POSTAL_CODE = 12;
    public static final int MAX_TAX_IDS = 10;

    /** Company / registration number: letters and digits, then also space, dot, slash or hyphen. */
    public static final Pattern REGISTRATION_NUMBER = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9 ./-]*$");

    /** Tax id value patterns, checked after upper-casing and removing all whitespace. */
    public static final Pattern TAX_PAN = Pattern.compile("^[A-Z]{5}[0-9]{4}[A-Z]$");
    public static final Pattern TAX_GSTIN = Pattern.compile("^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]$");
    public static final Pattern TAX_TAN = Pattern.compile("^[A-Z]{4}[0-9]{5}[A-Z]$");
    public static final Pattern TAX_TRN = Pattern.compile("^[0-9]{15}$");
    public static final Pattern TAX_VAT_SA = Pattern.compile("^3[0-9]{13}3$");
    public static final Pattern TAX_VAT_GB = Pattern.compile("^(GB)?([0-9]{9}|[0-9]{12}|GD[0-9]{3}|HA[0-9]{3})$");
    public static final Pattern TAX_CRN_GB = Pattern.compile("^([0-9]{8}|[A-Z]{2}[0-9]{6})$");
    public static final Pattern TAX_EIN = Pattern.compile("^[0-9]{2}-?[0-9]{7}$");
    public static final Pattern TAX_GENERIC = Pattern.compile("^[A-Z0-9][A-Z0-9./-]{2,39}$");

    /** Country codes of the tax types (informational: shown by the UI, not required to equal the registration country). */
    public static final String COUNTRY_IN = "IN";
    public static final String COUNTRY_AE = "AE";
    public static final String COUNTRY_SA = "SA";
    public static final String COUNTRY_GB = "GB";
    public static final String COUNTRY_US = "US";
    public static final String COUNTRY_QA = "QA";
    public static final String COUNTRY_HK = "HK";

    /** Every ISO 3166 alpha-2 country code. */
    public static final Set<String> ISO_COUNTRIES = Set.of(Locale.getISOCountries());

    /** Countries whose addresses must name a region (state). */
    public static final Set<String> REGION_REQUIRED_COUNTRIES = Set.of(COUNTRY_IN, COUNTRY_US);

    /** Countries without postal codes: the postal code must be empty. */
    public static final Set<String> NO_POSTAL_CODE_COUNTRIES = Set.of(COUNTRY_AE, COUNTRY_QA, COUNTRY_HK);

    /** Countries whose postal code is required and must match the pattern (after upper-casing). */
    public static final Map<String, Pattern> REQUIRED_POSTAL_CODES = Map.of(
            COUNTRY_IN, Pattern.compile("^[1-9][0-9]{5}$"),
            COUNTRY_US, Pattern.compile("^[0-9]{5}(-[0-9]{4})?$"),
            COUNTRY_SA, Pattern.compile("^[0-9]{5}(-[0-9]{4})?$"),
            COUNTRY_GB, Pattern.compile("^[A-Z]{1,2}[0-9][A-Z0-9]? ?[0-9][A-Z]{2}$"));

    /** Any other country: optional, but when given it must look like a postal code. */
    public static final Pattern OTHER_POSTAL_CODE = Pattern.compile("^[A-Z0-9][A-Z0-9 -]{1,11}$");

    /** Message keys (the web app translates them). */
    public static final String ERROR_INVALID_ENTITY_TYPE = "legalProfile.invalidEntityType";
    public static final String ERROR_INVALID_COUNTRY = "legalProfile.invalidCountry";
    public static final String ERROR_TOO_LONG = "legalProfile.tooLong";
    public static final String ERROR_INVALID_REGISTRATION_NUMBER = "legalProfile.invalidRegistrationNumber";
    public static final String ERROR_INVALID_TAX_TYPE = "legalProfile.invalidTaxType";
    public static final String ERROR_DUPLICATE_TAX_TYPE = "legalProfile.duplicateTaxType";
    public static final String ERROR_INVALID_TAX_ID = "legalProfile.invalidTaxId";
    public static final String ERROR_TOO_MANY_TAX_IDS = "legalProfile.tooManyTaxIds";
    public static final String ERROR_INVALID_ADDRESS_KIND = "legalProfile.invalidAddressKind";
    public static final String ERROR_DUPLICATE_ADDRESS_KIND = "legalProfile.duplicateAddressKind";
    public static final String ERROR_ADDRESS_REQUIRED = "legalProfile.addressRequired";
    public static final String ERROR_INVALID_POSTAL_CODE = "legalProfile.invalidPostalCode";
    public static final String ERROR_STALE_VERSION = "legalProfile.staleVersion";
    /** Not a contract key: the generic "organization not found" answer, as the other organization endpoints give it. */
    public static final String ERROR_ORGANIZATION_NOT_FOUND = "Organization not found";

    /** Field names, used as the field path of a refusal and as keys of the audit trail's before / after values. */
    public static final String FIELD_ENTITY_TYPE = "entityType";
    public static final String FIELD_LEGAL_NAME = "legalName";
    public static final String FIELD_TRADING_NAME = "tradingName";
    public static final String FIELD_REGISTRATION_NUMBER = "registrationNumber";
    public static final String FIELD_REGISTRATION_COUNTRY = "registrationCountry";
    public static final String FIELD_TAX_REGISTERED = "taxRegistered";
    public static final String FIELD_TAX_IDS = "taxIds";
    public static final String FIELD_ADDRESSES = "addresses";
    public static final String FIELD_TYPE = "type";
    public static final String FIELD_VALUE = "value";
    public static final String FIELD_KIND = "kind";
    public static final String FIELD_LINE1 = "line1";
    public static final String FIELD_LINE2 = "line2";
    public static final String FIELD_CITY = "city";
    public static final String FIELD_REGION = "region";
    public static final String FIELD_POSTAL_CODE = "postalCode";
    public static final String FIELD_COUNTRY = "country";
    public static final String FIELD_VERSION = "version";

    /** Entity type of the domain audit events. */
    public static final String AUDIT_ENTITY_TYPE = "ORGANIZATION_LEGAL_PROFILE";

    private LegalProfileConstants() {
    }
}
