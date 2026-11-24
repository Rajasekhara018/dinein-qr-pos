package com.heuristiq.uam.organization.legalprofile.domain;

import java.util.List;

/** A legal profile as sent, before normalisation and validation (see {@link LegalProfileValidator}). */
public record LegalProfileDraft(String entityType, String legalName, String tradingName, String registrationNumber,
                                String registrationCountry, Boolean taxRegistered, List<TaxIdDraft> taxIds,
                                List<AddressDraft> addresses) {

    /** One tax id as sent. */
    public record TaxIdDraft(String type, String value) {
    }

    /** One address as sent. */
    public record AddressDraft(String kind, String line1, String line2, String city, String region, String postalCode,
                               String country) {
    }
}
