package com.heuristiq.uam.session.repository.mongo;

import com.heuristiq.uam.session.UserSession;
import com.heuristiq.uam.session.persistence.mongo.UserSessionDocument;
import com.heuristiq.uam.session.repository.UserSessionRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@ConditionalOnProperty(prefix = "railcab.uam.persistence", name = "type", havingValue = "mongo", matchIfMissing = true)
class UserSessionMongoRepositoryAdapter implements UserSessionRepository {
    private final UserSessionMongoSpringRepository springRepository;

    UserSessionMongoRepositoryAdapter(UserSessionMongoSpringRepository springRepository) {
        this.springRepository = springRepository;
    }

    @Override
    public UserSession save(UserSession session) {
        return toDomain(springRepository.save(toDocument(session)));
    }

    @Override
    public List<UserSession> saveAll(List<UserSession> sessions) {
        List<UserSessionDocument> docs = sessions.stream().map(this::toDocument).toList();
        return springRepository.saveAll(docs).stream().map(this::toDomain).toList();
    }

    @Override
    public List<UserSession> findByUserIdAndActiveTrue(String userId) {
        return springRepository.findByUserIdAndActiveTrue(userId).stream().map(this::toDomain).toList();
    }

    @Override
    public Optional<UserSession> findBySessionId(String sessionId) {
        return springRepository.findBySessionId(sessionId).map(this::toDomain);
    }

    private UserSession toDomain(UserSessionDocument doc) {
        return UserSession.builder()
                .id(doc.getId())
                .sessionId(doc.getSessionId())
                .userId(doc.getUserId())
                .credentialId(doc.getCredentialId())
                .deviceId(doc.getDeviceId())
                .accessTokenId(doc.getAccessTokenId())
                .refreshTokenHash(doc.getRefreshTokenHash())
                .provider(doc.getProvider())
                .ipAddress(doc.getIpAddress())
                .country(doc.getCountry())
                .state(doc.getState())
                .city(doc.getCity())
                .browser(doc.getBrowser())
                .operatingSystem(doc.getOperatingSystem())
                .deviceType(doc.getDeviceType())
                .userAgent(doc.getUserAgent())
                .loginAt(doc.getLoginAt())
                .lastActivityAt(doc.getLastActivityAt())
                .expiresAt(doc.getExpiresAt())
                .logoutAt(doc.getLogoutAt())
                .logoutReason(doc.getLogoutReason())
                .active(doc.getActive())
                .rememberMe(doc.getRememberMe())
                .revoked(doc.getRevoked())
                .forcedLogout(doc.getForcedLogout())
                .mfaVerified(doc.getMfaVerified())
                .authMethod(doc.getAuthMethod())
                .metadata(doc.getMetadata())
                .createdAt(doc.getCreatedAt())
                .updatedAt(doc.getUpdatedAt())
                .version(doc.getVersion())
                .build();
    }

    private UserSessionDocument toDocument(UserSession session) {
        return UserSessionDocument.builder()
                .id(session.getId())
                .sessionId(session.getSessionId())
                .userId(session.getUserId())
                .credentialId(session.getCredentialId())
                .deviceId(session.getDeviceId())
                .accessTokenId(session.getAccessTokenId())
                .refreshTokenHash(session.getRefreshTokenHash())
                .provider(session.getProvider())
                .ipAddress(session.getIpAddress())
                .country(session.getCountry())
                .state(session.getState())
                .city(session.getCity())
                .browser(session.getBrowser())
                .operatingSystem(session.getOperatingSystem())
                .deviceType(session.getDeviceType())
                .userAgent(session.getUserAgent())
                .loginAt(session.getLoginAt())
                .lastActivityAt(session.getLastActivityAt())
                .expiresAt(session.getExpiresAt())
                .logoutAt(session.getLogoutAt())
                .logoutReason(session.getLogoutReason())
                .active(session.getActive())
                .rememberMe(session.getRememberMe())
                .revoked(session.getRevoked())
                .forcedLogout(session.getForcedLogout())
                .mfaVerified(session.getMfaVerified())
                .authMethod(session.getAuthMethod())
                .metadata(session.getMetadata())
                .createdAt(session.getCreatedAt())
                .updatedAt(session.getUpdatedAt())
                .version(session.getVersion())
                .build();
    }
}
