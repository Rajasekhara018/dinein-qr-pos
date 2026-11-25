package com.heuristiq.identity.pos.adapter.in;

import com.heuristiq.identity.organization.legalprofile.domain.TaxId;
import com.heuristiq.identity.organization.legalprofile.domain.TaxIdType;
import com.heuristiq.identity.pos.domain.LegalEntityDraft;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * Request body of the legal-entity POST and PUT. Fields are plain text on purpose: they are normalised and checked by the domain
 * validator, which answers with the translatable {@code pos.*} keys rather than generic bean-validation messages.
 */
@Schema(description = "A legal entity: the company that issues invoices in one country.")
public record LegalEntityRequest(
        @Schema(description = "ISO 3166 alpha-2 country; cannot change once the entity has branches", example = "IN") String country,
        @Schema(description = "Registered legal name, at most 200 characters") String legalName,
        @Schema(description = "ISO 4217 currency; defaults to the country's currency") String currency,
        @Schema(description = "Tax identifiers; each must fit the entity's country") List<TaxIdItem> taxIds,
        PosAddressItem address,
        @Schema(description = "Invoice number prefix, 1 to 10 of A-Z, 0-9 and dash") String invoicePrefix,
        @Schema(description = "Month the financial year starts, 1 to 12; defaults to the country's usual month") Integer financialYearStartMonth,
        @Schema(description = "Version read with the GET; required on PUT, ignored on POST") Long version) {

    @Schema(description = "One tax identifier.")
    public record TaxIdItem(
            @Schema(description = "PAN, GSTIN, TAN, TRN, VAT_SA, VAT_DE ...", example = "GSTIN") String type,
            @Schema(description = "The identifier; whitespace is removed and letters upper-cased") String value) {
    }

    LegalEntityDraft toDraft() {
        return new LegalEntityDraft(country, legalName, currency,
                taxIds == null ? null : taxIds.stream().map(t -> t == null ? null : new TaxId(parse(t.type()), t.value())).toList(),
                address == null ? null : address.toDomain(), invoicePrefix, financialYearStartMonth);
    }

    private static TaxIdType parse(String type) {
        if (type == null) {
            return null;
        }
        try {
            return TaxIdType.valueOf(type.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
