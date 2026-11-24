package com.heuristiq.uam.organization.legalprofile.domain;

/** One validated address of the legal profile; optional parts are null, never blank. */
public record LegalAddress(AddressKind kind, String line1, String line2, String city, String region, String postalCode,
                           String country) {
}
