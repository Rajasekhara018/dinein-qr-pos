package com.heuristiq.identity.organization.legalprofile.adapter.in;

import com.heuristiq.identity.organization.legalprofile.domain.LegalProfileDraft;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * Request body of the legal-profile PUT. Fields are plain text on purpose: they are normalised and checked by the domain
 * validator, which answers with the translatable {@code legalProfile.*} keys rather than generic bean-validation messages.
 */
@Schema(description = "The whole legal profile; replaces what is stored (tax ids and addresses as sets).")
public record LegalProfileRequest(
        @Schema(description = "INDIVIDUAL, SOLE_PROPRIETOR, PARTNERSHIP, LLP, PRIVATE_COMPANY, PUBLIC_COMPANY, NON_PROFIT, GOVERNMENT or OTHER",
                example = "PRIVATE_COMPANY") String entityType,
        @Schema(description = "Registered legal name, at most 200 characters (stored on the organization)") String legalName,
        @Schema(description = "Trading name, at most 200 characters") String tradingName,
        @Schema(description = "Company registration number, at most 50 characters") String registrationNumber,
        @Schema(description = "ISO 3166 alpha-2 country of registration", example = "IN") String registrationCountry,
        @Schema(description = "Registered for tax (indirect tax such as GST / VAT)") Boolean taxRegistered,
        @Schema(description = "At most 10, each type once") List<TaxIdItem> taxIds,
        @Schema(description = "REGISTERED and OPERATING, each at most once") List<AddressItem> addresses,
        @Schema(description = "Version read with the GET; null for the first save") Long version) {

    @Schema(description = "One tax identifier.")
    public record TaxIdItem(
            @Schema(description = "PAN, GSTIN, TAN, TRN, VAT_SA, VAT_GB, CRN_GB, EIN or TAX_ID", example = "PAN") String type,
            @Schema(description = "The identifier; whitespace is removed and letters upper-cased", example = "ABCDE1234F") String value) {
    }

    @Schema(description = "One address.")
    public record AddressItem(
            @Schema(description = "REGISTERED or OPERATING") String kind,
            String line1, String line2, String city,
            @Schema(description = "State or region; required in IN and US") String region,
            @Schema(description = "Postal code checked by country; must be empty in AE, QA and HK") String postalCode,
            @Schema(description = "ISO 3166 alpha-2 country", example = "IN") String country) {
    }

    LegalProfileDraft toDraft() {
        return new LegalProfileDraft(entityType, legalName, tradingName, registrationNumber, registrationCountry, taxRegistered,
                taxIds == null ? null : taxIds.stream()
                        .map(t -> t == null ? null : new LegalProfileDraft.TaxIdDraft(t.type(), t.value())).toList(),
                addresses == null ? null : addresses.stream()
                        .map(a -> a == null ? null : new LegalProfileDraft.AddressDraft(a.kind(), a.line1(), a.line2(), a.city(),
                                a.region(), a.postalCode(), a.country())).toList());
    }
}
