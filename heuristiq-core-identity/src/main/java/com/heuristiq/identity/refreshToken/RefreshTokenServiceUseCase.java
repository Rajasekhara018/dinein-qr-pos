package com.heuristiq.identity.refreshToken;

import com.heuristiq.identity.enums.UamEnums.PrincipalType;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface RefreshTokenServiceUseCase {

    record IssueInput(String principalId, PrincipalType principalType, String clientId, String tokenHash,
                       String sessionId, String deviceId, LocalDateTime expiresAt, String ipAddress, String userAgent) {}
    record IssueOutput(RefreshToken token) {}
    IssueOutput issue(IssueInput input);

    record RevokeInput(String tokenId) {}
    record RevokeOutput() {}
    RevokeOutput revoke(RevokeInput input);

    record RevokeAllForPrincipalInput(String principalId) {}
    record RevokeAllForPrincipalOutput() {}
    RevokeAllForPrincipalOutput revokeAllForPrincipal(RevokeAllForPrincipalInput input);

    record ListActiveForPrincipalInput(String principalId) {}
    record ListActiveForPrincipalOutput(List<RefreshToken> tokens) {}
    ListActiveForPrincipalOutput listActiveForPrincipal(ListActiveForPrincipalInput input);

    /** A valid token: exists, not revoked, not expired. Used to redeem a refresh token on {@code /refresh}. */
    record FindValidByHashInput(String tokenHash) {}
    record FindValidByHashOutput(Optional<RefreshToken> token) {}
    FindValidByHashOutput findValidByHash(FindValidByHashInput input);

    /**
     * A token that may be presented on {@code /refresh}: not expired, and either still active or revoked by rotation
     * (a rotated token is then judged by {@link #rotate}: inside the grace window it gets the family's current
     * successor, outside it the whole family is revoked). Tokens revoked for any other reason are not returned.
     */
    record FindRedeemableByHashInput(String tokenHash) {}
    record FindRedeemableByHashOutput(Optional<RefreshToken> token) {}
    FindRedeemableByHashOutput findRedeemableByHash(FindRedeemableByHashInput input);

    /**
     * Reuse detection, run first on every refresh, before any check that could refuse it for another reason (an inactive
     * account, an organization or application refusal): a rotated token presented outside the grace window is a stolen
     * token, so the whole family is revoked here and the caller refuses the refresh.
     *
     * @return true when the token was a reuse (family revoked); false for an active token or a replay inside the window
     */
    boolean revokeFamilyOnReuse(RefreshToken presented);

    /**
     * Redeems {@code presented} (whose raw value is {@code presentedRaw}).
     * <ul>
     *   <li>Active token: atomically marked ROTATED; a successor in the same family is issued ({@code graceReplay=false}).
     *       Of two concurrent redemptions only one rotates; the other is handled as a replay.</li>
     *   <li>Rotated within the grace window and under the redemption allowance: the family's current successor is
     *       returned unchanged ({@code graceReplay=true}); no new token is minted.</li>
     *   <li>Otherwise a 401 {@code REFRESH_TOKEN_INVALID}; a rotated token reused outside the window also revokes its family.</li>
     * </ul>
     */
    record RotateInput(RefreshToken presented, String presentedRaw, LocalDateTime newExpiresAt) {}
    record RotateOutput(RefreshToken token, String rawToken, boolean graceReplay) {}
    RotateOutput rotate(RotateInput input);
}
