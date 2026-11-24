package com.heuristiq.uam.refreshToken.repository.mongo;

import com.heuristiq.uam.refreshToken.RefreshToken;
import com.heuristiq.uam.refreshToken.RefreshTokenConstants;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import java.time.LocalDateTime;
import com.heuristiq.uam.refreshToken.persistance.mongo.RefreshTokenDocument;
import com.heuristiq.uam.refreshToken.RefreshTokenRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@ConditionalOnProperty(prefix = "railcab.uam.persistence", name = "type", havingValue = "mongo", matchIfMissing = true)
class RefreshTokenMongoRepositoryAdapter implements RefreshTokenRepository {
    private final RefreshTokenMongoSpringRepository springRepository;
    private final MongoTemplate mongoTemplate;

    RefreshTokenMongoRepositoryAdapter(RefreshTokenMongoSpringRepository springRepository, MongoTemplate mongoTemplate) {
        this.springRepository = springRepository;
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public RefreshToken save(RefreshToken refreshToken) {
        return toDomain(springRepository.save(toDocument(refreshToken)));
    }

    @Override
    public List<RefreshToken> saveAll(List<RefreshToken> refreshTokens) {
        List<RefreshTokenDocument> docs = refreshTokens.stream().map(this::toDocument).toList();
        return springRepository.saveAll(docs).stream().map(this::toDomain).toList();
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

    /** Single-document conditional update: atomic in MongoDB, so only one of two concurrent rotations matches. */
    @Override
    public boolean markRotated(String id, String successorTokenId, String familyId, LocalDateTime at) {
        Query query = Query.query(Criteria.where(RefreshTokenMongoFields.ID).is(id).and(RefreshTokenMongoFields.REVOKED).ne(true));
        Update update = new Update()
                .set(RefreshTokenMongoFields.REVOKED, true)
                .set(RefreshTokenMongoFields.REVOKED_REASON, RefreshTokenConstants.REASON_ROTATED)
                .set(RefreshTokenMongoFields.REVOKED_AT, at)
                .set(RefreshTokenMongoFields.LAST_USED_AT, at)
                .set(RefreshTokenMongoFields.SUCCESSOR_TOKEN_ID, successorTokenId)
                .set(RefreshTokenMongoFields.FAMILY_ID, familyId)
                .set(RefreshTokenMongoFields.AUDIT_UPDATED_AT, at);
        return mongoTemplate.updateFirst(query, update, RefreshTokenDocument.class).getModifiedCount() == 1;
    }

    @Override
    public boolean claimGraceRedemption(String id, int maxRedemptions) {
        Query query = Query.query(Criteria.where(RefreshTokenMongoFields.ID).is(id)
                .and(RefreshTokenMongoFields.REVOKED).is(true)
                .and(RefreshTokenMongoFields.REVOKED_REASON).is(RefreshTokenConstants.REASON_ROTATED)
                .orOperator(Criteria.where(RefreshTokenMongoFields.GRACE_REDEMPTIONS).is(null),
                        Criteria.where(RefreshTokenMongoFields.GRACE_REDEMPTIONS).lt(maxRedemptions)));
        return mongoTemplate.updateFirst(query, new Update().inc(RefreshTokenMongoFields.GRACE_REDEMPTIONS, 1),
                RefreshTokenDocument.class).getModifiedCount() == 1;
    }

    private RefreshToken toDomain(RefreshTokenDocument doc) {
        return RefreshToken.builder()
                .id(doc.getId())
                .principalId(doc.getPrincipalId())
                .principalType(doc.getPrincipalType())
                .clientId(doc.getClientId())
                .tokenId(doc.getTokenId())
                .tokenHash(doc.getTokenHash())
                .jwtId(doc.getJwtId())
                .sessionId(doc.getSessionId())
                .deviceId(doc.getDeviceId())
                .issuedAt(doc.getIssuedAt())
                .expiresAt(doc.getExpiresAt())
                .lastUsedAt(doc.getLastUsedAt())
                .revoked(doc.getRevoked())
                .revokedReason(doc.getRevokedReason())
                .revokedAt(doc.getRevokedAt())
                .familyId(doc.getFamilyId())
                .successorTokenId(doc.getSuccessorTokenId())
                .graceRedemptions(doc.getGraceRedemptions())
                .rotationCount(doc.getRotationCount())
                .ipAddress(doc.getIpAddress())
                .userAgent(doc.getUserAgent())
                .audit(doc.getAudit())
                .build();
    }

    private RefreshTokenDocument toDocument(RefreshToken refreshToken) {
        return RefreshTokenDocument.builder()
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
