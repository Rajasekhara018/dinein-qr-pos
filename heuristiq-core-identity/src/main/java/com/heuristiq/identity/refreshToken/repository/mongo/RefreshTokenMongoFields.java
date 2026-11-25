package com.heuristiq.identity.refreshToken.repository.mongo;

/** Field names of {@code refresh_tokens} documents used by the adapter's conditional updates. */
final class RefreshTokenMongoFields {

    static final String ID = "id";
    static final String REVOKED = "revoked";
    static final String REVOKED_REASON = "revokedReason";
    static final String REVOKED_AT = "revokedAt";
    static final String LAST_USED_AT = "lastUsedAt";
    static final String SUCCESSOR_TOKEN_ID = "successorTokenId";
    static final String FAMILY_ID = "familyId";
    static final String GRACE_REDEMPTIONS = "graceRedemptions";
    static final String AUDIT_UPDATED_AT = "audit.updatedAt";

    private RefreshTokenMongoFields() {
    }
}
