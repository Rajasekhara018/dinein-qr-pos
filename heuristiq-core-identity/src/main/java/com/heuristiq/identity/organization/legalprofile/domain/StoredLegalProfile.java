package com.heuristiq.identity.organization.legalprofile.domain;

import java.time.Instant;

/**
 * A saved legal profile with its optimistic-lock version and who saved it last. The stores keep everything except the
 * legal name, which lives on the organization record; {@code profile().legalName()} is null as read from a store.
 */
public record StoredLegalProfile(LegalProfile profile, long version, Instant updatedAt, String updatedBy) {
}
