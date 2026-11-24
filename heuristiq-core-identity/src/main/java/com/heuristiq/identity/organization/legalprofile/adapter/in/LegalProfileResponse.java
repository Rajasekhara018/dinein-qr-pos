package com.heuristiq.uam.organization.legalprofile.adapter.in;

import com.heuristiq.uam.organization.legalprofile.application.LegalProfileUseCase.LegalProfileView;
import com.heuristiq.uam.organization.legalprofile.domain.LegalProfile;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

/** Answer of the legal-profile endpoints; all fields null / empty (version null) when nothing has been saved yet. */
@Schema(description = "An organization's legal profile.")
public record LegalProfileResponse(
        String entityType,
        String legalName,
        String tradingName,
        String registrationNumber,
        String registrationCountry,
        boolean taxRegistered,
        List<TaxIdItem> taxIds,
        List<AddressItem> addresses,
        @Schema(description = "Send it back with the PUT; null when nothing is saved yet") Long version,
        Instant updatedAt,
        String updatedBy) {

    @Schema(description = "One tax identifier.")
    public record TaxIdItem(String type, String value) {
    }

    @Schema(description = "One address.")
    public record AddressItem(String kind, String line1, String line2, String city, String region, String postalCode, String country) {
    }

    static LegalProfileResponse from(LegalProfileView view) {
        LegalProfile p = view.profile();
        return new LegalProfileResponse(p.entityType() == null ? null : p.entityType().name(), p.legalName(), p.tradingName(),
                p.registrationNumber(), p.registrationCountry(), p.taxRegistered(),
                p.taxIds().stream().map(t -> new TaxIdItem(t.type().name(), t.value())).toList(),
                p.addresses().stream().map(a -> new AddressItem(a.kind().name(), a.line1(), a.line2(), a.city(), a.region(),
                        a.postalCode(), a.country())).toList(),
                view.version(), view.updatedAt(), view.updatedBy());
    }
}
