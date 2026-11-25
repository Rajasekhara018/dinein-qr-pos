package com.heuristiq.identity.refreshToken.repository.jpa;

import com.heuristiq.identity.refreshToken.RefreshToken;
import com.heuristiq.identity.refreshToken.RefreshTokenConstants;
import java.time.LocalDateTime;
import com.heuristiq.identity.refreshToken.persistance.jpa.RefreshTokenEntity;
import com.heuristiq.identity.refreshToken.RefreshTokenRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@ConditionalOnProperty(prefix = "railcab.uam.persistence", name = "type", havingValue = "postgres")
class RefreshTokenJpaRepositoryAdapter implements RefreshTokenRepository {
    private final RefreshTokenJpaSpringRepository springRepository;

    RefreshTokenJpaRepositoryAdapter(RefreshTokenJpaSpringRepository springRepository) {
        this.springRepository = springRepository;
    }

    @Override
    public RefreshToken save(RefreshToken refreshToken) {
        return toDomain(springRepository.save(toEntity(refreshToken)));
    }

    @Override
    public List<RefreshToken> saveAll(List<RefreshToken> refreshTokens) {
        List<RefreshTokenEntity> entities = refreshTokens.stream().map(this::toEntity).toList();
        return springRepository.saveAll(entities).stream().map(this::toDomain).toList();
    }

    @Override
    public Optional<RefreshToken> findByTokenId(String tokenId) {
        return springRepository.findByTokenId(tokenId).map(this::toDomain);
    }

    @Override
    public Optional<RefreshToken> findByTokenHash(String tokenHash) {
        return springRepository.findByTokenHash(tokenHash).map(this::toDomain);
    }

    @Override
    public List<RefreshToken> findByPrincipalIdAndRevokedFalse(String principalId) {
        return springRepository.findByPrincipalIdAndRevokedFalse(principalId).stream().map(this::toDomain).toList();
    }

    @Override
    public List<RefreshToken> findByFamilyIdAndRevokedFalse(String familyId) {
        return springRepository.findByFamilyIdAndRevokedFalse(familyId).stream().map(this::toDomain).toList();
    }

    @Override
    public boolean markRotated(String id, String successorTokenId, String familyId, LocalDateTime at) {
        return springRepository.markRotated(id, successorTokenId, familyId, at, RefreshTokenConstants.REASON_ROTATED) == 1;
    }

    @Override
    public boolean claimGraceRedemption(String id, int maxRedemptions) {
        return springRepository.claimGraceRedemption(id, maxRedemptions, RefreshTokenConstants.REASON_ROTATED) == 1;
    }

    private RefreshToken toDomain(RefreshTokenEntity entity) {
        return RefreshToken.builder()
                .id(entity.getId())
                .principalId(entity.getPrincipalId())
                .principalType(entity.getPrincipalType())
                .clientId(entity.getClientId())
                .tokenId(entity.getTokenId())
                .tokenHash(entity.getTokenHash())
                .jwtId(entity.getJwtId())
                .sessionId(entity.getSessionId())
                .deviceId(entity.getDeviceId())
                .issuedAt(entity.getIssuedAt())
                .expiresAt(entity.getExpiresAt())
                .lastUsedAt(entity.getLastUsedAt())
                .revoked(entity.getRevoked())
                .revokedReason(entity.getRevokedReason())
                .revokedAt(entity.getRevokedAt())
                .familyId(entity.getFamilyId())
                .successorTokenId(entity.getSuccessorTokenId())
                .graceRedemptions(entity.getGraceRedemptions())
                .rotationCount(entity.getRotationCount())
                .ipAddress(entity.getIpAddress())
                .userAgent(entity.getUserAgent())
                .audit(entity.getAudit())
                .build();
    }

    private RefreshTokenEntity toEntity(RefreshToken refreshToken) {
        return RefreshTokenEntity.builder()
                .id(refreshToken.getId())
                .principalId(refreshToken.getPrincipalId())
                .principalType(refreshToken.getPrincipalType())
                .clientId(refreshToken.getClientId())
                .tokenId(refreshToken.getTokenId())
                .tokenHash(refreshToken.getTokenHash())
                .jwtId(refreshToken.getJwtId())
                .sessionId(refreshToken.getSessionId())
                .deviceId(refreshToken.getDeviceId())
                .issuedAt(refreshToken.getIssuedAt())
                .expiresAt(refreshToken.getExpiresAt())
                .lastUsedAt(refreshToken.getLastUsedAt())
                .revoked(refreshToken.getRevoked())
                .revokedReason(refreshToken.getRevokedReason())
                .revokedAt(refreshToken.getRevokedAt())
                .familyId(refreshToken.getFamilyId())
                .successorTokenId(refreshToken.getSuccessorTokenId())
                .graceRedemptions(refreshToken.getGraceRedemptions())
                .rotationCount(refreshToken.getRotationCount())
                .ipAddress(refreshToken.getIpAddress())
                .userAgent(refreshToken.getUserAgent())
                .audit(refreshToken.getAudit())
                .build();
    }
}
