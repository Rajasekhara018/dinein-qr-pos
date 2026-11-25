package com.heuristiq.identity.organization.legalprofile.domain;

/** One validated tax identifier: its type and its normalised value (upper case, no whitespace). */
public record TaxId(TaxIdType type, String value) {
}
