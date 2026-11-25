package com.heuristiq.identity.pos.adapter.in;

import com.heuristiq.identity.pos.domain.LegalEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

/** Answer of the legal-entity endpoints. */
@Schema(description = "A legal entity of an organization.")
public record LegalEntityResponse(
        String id,
        String country,
        String legalName,
        String currency,
        List<LegalEntityRequest.TaxIdItem> taxIds,
        PosAddressItem address,
        String invoicePrefix,
        int financialYearStartMonth,
        String status,
        @Schema(description = "Send it back with the PUT") long version,
        Instant updatedAt,
        String updatedBy) {

    static LegalEntityResponse from(LegalEntity e) {
        return new LegalEntityResponse(e.id(), e.country(), e.legalName(), e.currency(),
                e.taxIds().stream().map(t -> new LegalEntityRequest.TaxIdItem(t.type().name(), t.value())).toList(),
                PosAddressItem.from(e.address()), e.invoicePrefix(), e.financialYearStartMonth(), e.status().name(), e.version(),
                e.updatedAt(), e.updatedBy());
    }
}
