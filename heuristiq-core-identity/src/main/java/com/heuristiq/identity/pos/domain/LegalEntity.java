package com.heuristiq.identity.pos.domain;

import com.heuristiq.identity.organization.legalprofile.domain.TaxId;
import java.time.Instant;
import java.util.List;

/** A stored legal entity (the company that issues invoices in one country) of a tenant. */
public record LegalEntity(String id, String tenantId, String country, String legalName, String currency, List<TaxId> taxIds,
        PosAddress address, String invoicePrefix, int financialYearStartMonth, PosStatus status, long version, Instant updatedAt,
        String updatedBy) {

    public LegalEntity {
        taxIds = taxIds == null ? List.of() : List.copyOf(taxIds);
    }
}
