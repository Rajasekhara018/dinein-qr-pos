package com.heuristiq.uam.session.repository.jpa;

import com.heuristiq.uam.session.UserSession;
import com.heuristiq.uam.session.persistence.jpa.UserSessionEntity;
import com.heuristiq.uam.session.repository.UserSessionRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@ConditionalOnProperty(prefix = "railcab.uam.persistence", name = "type", havingValue = "postgres")
class UserSessionJpaRepositoryAdapter implements UserSessionRepository {
    private final UserSessionJpaSpringRepository springRepository;

    UserSessionJpaRepositoryAdapter(UserSessionJpaSpringRepository springRepository) {
        this.springRepository = springRepository;
    }

    @Override
    public UserSession save(UserSession session) {
        return toDomain(springRepository.save(toEntity(session)));
    }

    @Override
    public List<UserSession> saveAll(List<UserSession> sessions) {
        List<UserSessionEntity> entities = sessions.stream().map(this::toEntity).toList();
        return springRepository.saveAll(entities).stream().map(this::toDomain).toList();
    }

    @Override
    public List<UserSession> findByUserIdAndActiveTrue(String userId) {
        return springRepository.findByUserIdAndActiveTrue(userId).stream().map(this::toDomain).toList();
    }

    @Override
    public Optional<UserSession> findBySessionId(String sessionId) {
        return springRepository.findBySessionId(sessionId).map(this::toDomain);
    }

    private UserSession toDomain(UserSessionEntity entity) {
        return UserSession.builder()
                .id(entity.getId())
                .sessionId(entity.getSessionId())
                .userId(entity.getUserId())
                .credentialId(entity.getCredentialId())
                .deviceId(entity.getDeviceId())
                .accessTokenId(entity.getAccessTokenId())
                .refreshTokenHash(entity.getRefreshTokenHash())
                .provider(entity.getProvider())
                .ipAddress(entity.getIpAddress())
                .country(entity.getCountry())
                .state(entity.getState())
                .city(entity.getCity())
                .browser(entity.getBrowser())
                .operatingSystem(entity.getOperatingSystem())
                .deviceType(entity.getDeviceType())
                .userAgent(entity.getUserAgent())
                .loginAt(entity.getLoginAt())
                .lastActivityAt(entity.getLastActivityAt())
                .expiresAt(entity.getExpiresAt())
                .logoutAt(entity.getLogoutAt())
                .logoutReason(entity.getLogoutReason())
                .active(entity.getActive())
                .rememberMe(entity.getRememberMe())
                .revoked(entity.getRevoked())
                .forcedLogout(entity.getForcedLogout())
                .mfaVerified(entity.getMfaVerified())
                .authMethod(entity.getAuthMethod())
                .metadata(entity.getMetadata())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .version(entity.getVersion())
                .build();
    }

    private UserSessionEntity toEntity(UserSession session) {
        return UserSessionEntity.builder()
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
