package com.heuristiq.uam.refreshToken;

import com.heuristiq.uam.common.UamException;
import com.heuristiq.uam.helper.AuditInfo;
import com.heuristiq.uam.refreshToken.config.RefreshTokenProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Issues, rotates and revokes UAM's own refresh tokens.
 *
 * <p>Rotation is a conditional update (only one of two racing redemptions wins). A token redeemed again shortly
 * after rotation (lost response, racing requests) is answered with the family's current successor; reuse after the
 * grace window is treated as theft and revokes every token of the family. See the Rider_UAM README, "Refresh tokens".
 */
@Service
public class RefreshTokenService implements RefreshTokenServiceUseCase {
    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);

    private final RefreshTokenRepository refreshTokenRepository;
    private final RefreshTokenSuccessorDeriver successorDeriver;
    private final RefreshTokenProperties properties;
    private final Clock clock;

    /** Uses the context's {@link Clock} when one is defined, else the system clock in the JVM zone (the zone stored timestamps use). */
    @Autowired
    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository, RefreshTokenSuccessorDeriver successorDeriver,
                               RefreshTokenProperties properties, ObjectProvider<Clock> clock) {
        this(refreshTokenRepository, successorDeriver, properties, clock.getIfUnique(Clock::systemDefaultZone));
    }

    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository, RefreshTokenSuccessorDeriver successorDeriver,
                               RefreshTokenProperties properties, Clock clock) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.successorDeriver = successorDeriver;
        this.properties = properties;
        this.clock = clock;
    }

    public IssueOutput issue(IssueInput input) {
        LocalDateTime now = now();
        String tokenId = UUID.randomUUID().toString();
        RefreshToken token = RefreshToken.builder()
                .principalId(input.principalId())
                .principalType(input.principalType())
                .clientId(input.clientId())
                .tokenId(tokenId)
                .tokenHash(input.tokenHash())
                .familyId(tokenId)
                .sessionId(input.sessionId())
                .deviceId(input.deviceId())
                .issuedAt(now)
                .expiresAt(input.expiresAt())
                .revoked(false)
                .rotationCount(0)
                .ipAddress(input.ipAddress())
                .userAgent(input.userAgent())
                .audit(AuditInfo.builder().createdAt(now).updatedAt(now).build())
                .build();
        return new IssueOutput(refreshTokenRepository.save(token));
    }

    public RevokeOutput revoke(RevokeInput input) {
        RefreshToken token = refreshTokenRepository.findByTokenId(input.tokenId())
                .orElseThrow(() -> UamException.notFound("Refresh token not found"));
        markRevoked(token, RefreshTokenConstants.REASON_REVOKED_BY_ADMIN, now());
        refreshTokenRepository.save(token);
        return new RevokeOutput();
    }

    public RevokeAllForPrincipalOutput revokeAllForPrincipal(RevokeAllForPrincipalInput input) {
        var tokens = refreshTokenRepository.findByPrincipalIdAndRevokedFalse(input.principalId());
        LocalDateTime now = now();
        tokens.forEach(token -> markRevoked(token, RefreshTokenConstants.REASON_REVOKED_BY_ADMIN, now));
        refreshTokenRepository.saveAll(tokens);
        return new RevokeAllForPrincipalOutput();
    }

    public ListActiveForPrincipalOutput listActiveForPrincipal(ListActiveForPrincipalInput input) {
        return new ListActiveForPrincipalOutput(refreshTokenRepository.findByPrincipalIdAndRevokedFalse(input.principalId()));
    }

    public FindValidByHashOutput findValidByHash(FindValidByHashInput input) {
        LocalDateTime now = now();
        return new FindValidByHashOutput(refreshTokenRepository.findByTokenHash(input.tokenHash())
                .filter(t -> !Boolean.TRUE.equals(t.getRevoked()))
                .filter(t -> notExpired(t, now)));
    }

    public FindRedeemableByHashOutput findRedeemableByHash(FindRedeemableByHashInput input) {
        LocalDateTime now = now();
        return new FindRedeemableByHashOutput(refreshTokenRepository.findByTokenHash(input.tokenHash())
                .filter(t -> !Boolean.TRUE.equals(t.getRevoked()) || isRotated(t))
                .filter(t -> notExpired(t, now)));
    }

    @Override
    public boolean revokeFamilyOnReuse(RefreshToken presented) {
        LocalDateTime now = now();
        if (presented == null || !isRotated(presented) || withinGrace(presented, now)) {
            return false;
        }
        revokeFamily(presented, now);
        return true;
    }

    public RotateOutput rotate(RotateInput input) {
        RefreshToken presented = input.presented();
        LocalDateTime now = now();
        if (!Boolean.TRUE.equals(presented.getRevoked())) {
            String successorTokenId = UUID.randomUUID().toString();
            String familyId = presented.getFamilyId() != null ? presented.getFamilyId() : presented.getTokenId();
            String raw = successorDeriver.deriveSuccessor(input.presentedRaw(), successorTokenId);
            // The successor is stored before the link to it, so a racing request that sees the link always finds it.
            RefreshToken next = refreshTokenRepository.save(successorOf(presented, successorTokenId, familyId,
                    RefreshTokenHashing.sha256Hex(raw), input.newExpiresAt(), now));
            if (refreshTokenRepository.markRotated(presented.getId(), successorTokenId, familyId, now)) {
                return new RotateOutput(next, raw, false);
            }
            // Lost the race: another request rotated it a moment ago. Retire the unused successor, then judge the
            // presented token again from what is stored now.
            markRevoked(next, RefreshTokenConstants.REASON_ROTATION_LOST, now);
            refreshTokenRepository.save(next);
            presented = refreshTokenRepository.findByTokenId(presented.getTokenId())
                    .orElseThrow(RefreshTokenFailures::invalid);
        }
        return replay(presented, input.presentedRaw(), now);
    }

    /** A token presented after it was revoked: the grace answer, or a refusal (revoking the family on reuse). */
    private RotateOutput replay(RefreshToken presented, String presentedRaw, LocalDateTime now) {
        if (!isRotated(presented)) {
            throw RefreshTokenFailures.invalid();
        }
        if (!withinGrace(presented, now)
                || !refreshTokenRepository.claimGraceRedemption(presented.getId(), properties.maxGraceRedemptions())) {
            revokeFamily(presented, now);
            throw RefreshTokenFailures.invalid();
        }
        return currentSuccessor(presented, presentedRaw, now).orElseThrow(RefreshTokenFailures::invalid);
    }

    /**
     * Follows the successor links from {@code presented}, re-deriving each raw token, to the family's active token.
     * Empty when the chain ends in a token revoked for another reason (logout) or cannot be re-derived (a successor
     * issued before derivation existed).
     */
    private Optional<RotateOutput> currentSuccessor(RefreshToken presented, String presentedRaw, LocalDateTime now) {
        RefreshToken current = presented;
        String raw = presentedRaw;
        for (int step = 0; step < RefreshTokenConstants.MAX_SUCCESSOR_WALK && current.getSuccessorTokenId() != null; step++) {
            Optional<RefreshToken> successor = refreshTokenRepository.findByTokenId(current.getSuccessorTokenId());
            if (successor.isEmpty()) {
                return Optional.empty();
            }
            raw = successorDeriver.deriveSuccessor(raw, current.getSuccessorTokenId());
            RefreshToken next = successor.get();
            if (!RefreshTokenHashing.sameHash(RefreshTokenHashing.sha256Hex(raw), next.getTokenHash())) {
                return Optional.empty();
            }
            if (!Boolean.TRUE.equals(next.getRevoked())) {
                return notExpired(next, now) ? Optional.of(new RotateOutput(next, raw, true)) : Optional.empty();
            }
            if (!isRotated(next)) {
                return Optional.empty();
            }
            current = next;
        }
        return Optional.empty();
    }

    /** Revokes every active token of the presented token's family (legacy tokens without a family: its sign-in session). */
    private void revokeFamily(RefreshToken presented, LocalDateTime now) {
        List<RefreshToken> active = presented.getFamilyId() != null
                ? refreshTokenRepository.findByFamilyIdAndRevokedFalse(presented.getFamilyId())
                : refreshTokenRepository.findByPrincipalIdAndRevokedFalse(presented.getPrincipalId()).stream()
                        .filter(t -> presented.getSessionId() == null || presented.getSessionId().equals(t.getSessionId()))
                        .toList();
        active.forEach(token -> markRevoked(token, RefreshTokenConstants.REASON_REUSE_DETECTED, now));
        refreshTokenRepository.saveAll(active);
        log.warn("Refresh token reuse detected for principal {}: revoked {} active token(s) of family {}",
                presented.getPrincipalId(), active.size(), presented.getFamilyId());
    }

    private RefreshToken successorOf(RefreshToken presented, String tokenId, String familyId, String tokenHash,
                                     LocalDateTime expiresAt, LocalDateTime now) {
        return RefreshToken.builder()
                .principalId(presented.getPrincipalId())
                .principalType(presented.getPrincipalType())
                .clientId(presented.getClientId())
                .tokenId(tokenId)
                .tokenHash(tokenHash)
                .familyId(familyId)
                .sessionId(presented.getSessionId())
                .deviceId(presented.getDeviceId())
                .issuedAt(now)
                .expiresAt(expiresAt)
                .revoked(false)
                .rotationCount(presented.getRotationCount() == null ? 1 : presented.getRotationCount() + 1)
                .ipAddress(presented.getIpAddress())
                .userAgent(presented.getUserAgent())
                .audit(AuditInfo.builder().createdAt(now).updatedAt(now).build())
                .build();
    }

    private boolean withinGrace(RefreshToken token, LocalDateTime now) {
        long graceSeconds = properties.reuseGraceSeconds();
        return graceSeconds > 0 && token.getRevokedAt() != null
                && !now.isAfter(token.getRevokedAt().plusSeconds(graceSeconds));
    }

    private static boolean isRotated(RefreshToken token) {
        return Boolean.TRUE.equals(token.getRevoked()) && RefreshTokenConstants.REASON_ROTATED.equals(token.getRevokedReason());
    }

    private static boolean notExpired(RefreshToken token, LocalDateTime now) {
        return token.getExpiresAt() == null || token.getExpiresAt().isAfter(now);
    }

    private static void markRevoked(RefreshToken token, String reason, LocalDateTime now) {
        token.setRevoked(true);
        token.setRevokedReason(reason);
        token.setRevokedAt(now);
        if (token.getAudit() != null) {
            token.getAudit().setUpdatedAt(now);
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
