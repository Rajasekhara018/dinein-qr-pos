package com.heuristiq.identity.refreshToken.repository.jpa;

/** JPQL of the conditional refresh-token updates; kept apart so a migration test can run the exact statements. */
public final class RefreshTokenJpaQueries {

    /** Rotation wins only while the row is still active. */
    public static final String MARK_ROTATED = "update RefreshTokenEntity t set t.revoked = true, t.revokedReason = :reason, "
            + "t.revokedAt = :at, t.lastUsedAt = :at, t.successorTokenId = :successor, t.familyId = :family, "
            + "t.audit.updatedAt = :at where t.id = :id and (t.revoked = false or t.revoked is null)";

    /** Counts one grace redemption of a rotated row while under the allowance. */
    public static final String CLAIM_GRACE = "update RefreshTokenEntity t set t.graceRedemptions = coalesce(t.graceRedemptions, 0) + 1 "
            + "where t.id = :id and t.revoked = true and t.revokedReason = :reason "
            + "and (t.graceRedemptions is null or t.graceRedemptions < :max)";

    private RefreshTokenJpaQueries() {
    }
}
