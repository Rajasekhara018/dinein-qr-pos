package com.heuristiq.uam.refreshToken;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.heuristiq.uam.enums.UamEnums.PrincipalType;
import com.heuristiq.uam.helper.AuditInfo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Pure domain model -- see {@code persistence.mongo.RefreshTokenDocument} / {@code persistence.jpa.RefreshTokenEntity}.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class RefreshToken {

    private String id;

    private String principalId;

    private PrincipalType principalType;

    private String clientId;

    private String tokenId;

    @JsonIgnore
    private String tokenHash;

    private String jwtId;

    private String sessionId;

    private String deviceId;

    private LocalDateTime issuedAt;

    private LocalDateTime expiresAt;

    private LocalDateTime lastUsedAt;

    @Builder.Default
    private Boolean revoked = false;

    private String revokedReason;

    /** When the token was revoked (rotation, logout, reuse detection); starts the reuse-grace window for ROTATED. */
    private LocalDateTime revokedAt;

    /** One sign-in's chain of rotated tokens; every successor inherits it. Null on tokens issued before families existed. */
    private String familyId;

    /** Token id of the token that replaced this one at rotation (null while the token is active). */
    private String successorTokenId;

    /** How many times this rotated token was redeemed inside the reuse-grace window. */
    private Integer graceRedemptions;

    @Builder.Default
    private Integer rotationCount = 0;

    private String ipAddress;

    private String userAgent;

    private AuditInfo audit;
}
