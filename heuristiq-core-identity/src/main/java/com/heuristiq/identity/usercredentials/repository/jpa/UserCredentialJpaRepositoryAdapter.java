package com.heuristiq.uam.usercredentials.repository.jpa;

import com.heuristiq.uam.usercredentials.UserCredential;
import com.heuristiq.uam.enums.UamEnums.AuthProvider;
import com.heuristiq.uam.usercredentials.persistance.jpa.UserCredentialEntity;
import com.heuristiq.uam.usercredentials.UserCredentialRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@ConditionalOnProperty(prefix = "railcab.uam.persistence", name = "type", havingValue = "postgres")
class UserCredentialJpaRepositoryAdapter implements UserCredentialRepository {
    private final UserCredentialJpaSpringRepository springRepository;

    UserCredentialJpaRepositoryAdapter(UserCredentialJpaSpringRepository springRepository) {
        this.springRepository = springRepository;
    }

    @Override
    public UserCredential save(UserCredential credential) {
        return toDomain(springRepository.save(toEntity(credential)));
    }

    @Override
    public Optional<UserCredential> findById(String id) {
        return springRepository.findById(id).map(this::toDomain);
    }

    @Override
    public List<UserCredential> findByUserId(String userId) {
        return springRepository.findByUserId(userId).stream().map(this::toDomain).toList();
    }

    @Override
    public Optional<UserCredential> findByUserIdAndProvider(String userId, AuthProvider provider) {
        return springRepository.findByUserIdAndProvider(userId, provider).map(this::toDomain);
    }

    @Override
    public Optional<UserCredential> findByProviderAndProviderUserId(AuthProvider provider, String providerUserId) {
        return springRepository.findByProviderAndProviderUserId(provider, providerUserId).map(this::toDomain);
    }

    private UserCredential toDomain(UserCredentialEntity entity) {
        return UserCredential.builder()
                .id(entity.getId())
                .userId(entity.getUserId())
                .provider(entity.getProvider())
                .providerUserId(entity.getProviderUserId())
                .passwordHash(entity.getPasswordHash())
                .passwordSalt(entity.getPasswordSalt())
                .temporaryPasswordHash(entity.getTemporaryPasswordHash())
                .temporaryPasswordExpiry(entity.getTemporaryPasswordExpiry())
                .passwordChangedAt(entity.getPasswordChangedAt())
                .passwordExpiryAt(entity.getPasswordExpiryAt())
                .forcePasswordChange(entity.getForcePasswordChange())
                .primaryCredential(entity.getPrimaryCredential())
                .verified(entity.getVerified())
                .emailVerified(entity.getEmailVerified())
                .phoneVerified(entity.getPhoneVerified())
                .refreshToken(entity.getRefreshToken())
                .accessToken(entity.getAccessToken())
                .tokenExpiry(entity.getTokenExpiry())
                .lastAuthenticatedAt(entity.getLastAuthenticatedAt())
                .lastSyncAt(entity.getLastSyncAt())
                .metadata(entity.getMetadata())
                .active(entity.getActive())
                .createdAt(entity.getCreatedAt())
                .createdBy(entity.getCreatedBy())
                .updatedAt(entity.getUpdatedAt())
                .updatedBy(entity.getUpdatedBy())
                .version(entity.getVersion())
                .build();
    }

    private UserCredentialEntity toEntity(UserCredential credential) {
        return UserCredentialEntity.builder()
                .id(credential.getId())
                .userId(credential.getUserId())
                .provider(credential.getProvider())
                .providerUserId(credential.getProviderUserId())
                .passwordHash(credential.getPasswordHash())
                .passwordSalt(credential.getPasswordSalt())
                .temporaryPasswordHash(credential.getTemporaryPasswordHash())
                .temporaryPasswordExpiry(credential.getTemporaryPasswordExpiry())
                .passwordChangedAt(credential.getPasswordChangedAt())
                .passwordExpiryAt(credential.getPasswordExpiryAt())
                .forcePasswordChange(credential.getForcePasswordChange())
                .primaryCredential(credential.getPrimaryCredential())
                .verified(credential.getVerified())
                .emailVerified(credential.getEmailVerified())
                .phoneVerified(credential.getPhoneVerified())
                .refreshToken(credential.getRefreshToken())
                .accessToken(credential.getAccessToken())
                .tokenExpiry(credential.getTokenExpiry())
                .lastAuthenticatedAt(credential.getLastAuthenticatedAt())
                .lastSyncAt(credential.getLastSyncAt())
                .metadata(credential.getMetadata())
                .active(credential.getActive())
                .createdAt(credential.getCreatedAt())
                .createdBy(credential.getCreatedBy())
                .updatedAt(credential.getUpdatedAt())
                .updatedBy(credential.getUpdatedBy())
                .version(credential.getVersion())
                .build();
    }
}
