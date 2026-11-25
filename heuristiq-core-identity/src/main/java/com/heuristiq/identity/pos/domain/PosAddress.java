package com.heuristiq.identity.pos.domain;

/** A postal address of a legal entity or branch; {@code country} is an ISO 3166 alpha-2 code. */
public record PosAddress(String line1, String line2, String city, String region, String postalCode, String country) {
}
