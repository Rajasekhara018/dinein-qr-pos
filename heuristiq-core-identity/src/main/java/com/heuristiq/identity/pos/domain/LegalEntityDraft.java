package com.heuristiq.identity.pos.domain;

import com.heuristiq.identity.organization.legalprofile.domain.TaxId;
import java.util.List;

/** The fields a caller sends to create or change a legal entity; {@link PosValidator#legalEntity} normalises it. */
public record LegalEntityDraft(String country, String legalName, String currency, List<TaxId> taxIds, PosAddress address,
        String invoicePrefix, Integer financialYearStartMonth) {

    public LegalEntityDraft {
        taxIds = taxIds == null ? List.of() : List.copyOf(taxIds);
    }
}
