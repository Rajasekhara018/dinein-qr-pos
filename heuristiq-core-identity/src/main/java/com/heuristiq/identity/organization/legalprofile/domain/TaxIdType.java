package com.heuristiq.identity.organization.legalprofile.domain;

import java.util.regex.Pattern;

/**
 * The kinds of tax identifier an organization may record, each with the country it belongs to (informational, null for the
 * generic {@link #TAX_ID}) and the pattern its value must match after normalisation.
 */
public enum TaxIdType {
    PAN(LegalProfileConstants.COUNTRY_IN, LegalProfileConstants.TAX_PAN),
    GSTIN(LegalProfileConstants.COUNTRY_IN, LegalProfileConstants.TAX_GSTIN),
    TAN(LegalProfileConstants.COUNTRY_IN, LegalProfileConstants.TAX_TAN),
    TRN(LegalProfileConstants.COUNTRY_AE, LegalProfileConstants.TAX_TRN),
    VAT_SA(LegalProfileConstants.COUNTRY_SA, LegalProfileConstants.TAX_VAT_SA),
    VAT_GB(LegalProfileConstants.COUNTRY_GB, LegalProfileConstants.TAX_VAT_GB),
    CRN_GB(LegalProfileConstants.COUNTRY_GB, LegalProfileConstants.TAX_CRN_GB),
    EIN(LegalProfileConstants.COUNTRY_US, LegalProfileConstants.TAX_EIN),
    VAT_DE(LegalProfileConstants.COUNTRY_DE, LegalProfileConstants.TAX_VAT_DE),
    VAT_FR(LegalProfileConstants.COUNTRY_FR, LegalProfileConstants.TAX_VAT_FR),
    ABN(LegalProfileConstants.COUNTRY_AU, LegalProfileConstants.TAX_ABN),
    TAX_ID(null, LegalProfileConstants.TAX_GENERIC);

    private final String country;
    private final Pattern pattern;

    TaxIdType(String country, Pattern pattern) {
        this.country = country;
        this.pattern = pattern;
    }

    /** ISO country the identifier belongs to; null when it is not tied to one country. */
    public String country() {
        return country;
    }

    /** True when the (normalised) value is a well-formed identifier of this type. */
    public boolean accepts(String value) {
        return value != null && pattern.matcher(value).matches();
    }
}
