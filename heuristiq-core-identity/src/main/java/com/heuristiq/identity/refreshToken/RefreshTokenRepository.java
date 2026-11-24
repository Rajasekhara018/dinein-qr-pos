package com.heuristiq.uam.refreshToken;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface RefreshTokenRepository {
    RefreshToken save(RefreshToken refreshToken);

    List<RefreshToken> saveAll(List<RefreshToken> refreshTokens);

    Optional<RefreshToken> findByTokenId(String tokenId);

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    List<RefreshToken> findByPrincipalIdAndRevokedFalse(String principalId);

    /** Active tokens of one rotation family (reuse detection revokes them all). */
    List<RefreshToken> findByFamilyIdAndRevokedFalse(String familyId);

    /**
     * Atomically marks the token with storage id {@code id} as rotated, but only while it is still active
     * ({@code ... WHERE id = ? AND revoked = false}). Of two concurrent rotations of the same token exactly one gets
     * {@code true}; the other must treat the token as already rotated.
     */
    boolean markRotated(String id, String successorTokenId, String familyId, LocalDateTime at);

    /**
     * Atomically counts one grace-window redemption of a rotated token, but only while fewer than
     * {@code maxRedemptions} have been counted. {@code false} means the allowance is used up.
     */
    boolean claimGraceRedemption(String id, int maxRedemptions);
}
