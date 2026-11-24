package com.heuristiq.uam.usercredentials.repository.mongo;

import com.heuristiq.uam.usercredentials.UserCredential;
import com.heuristiq.uam.enums.UamEnums.AuthProvider;
import com.heuristiq.uam.usercredentials.persistance.mongo.UserCredentialDocument;
import com.heuristiq.uam.usercredentials.UserCredentialRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@ConditionalOnProperty(prefix = "railcab.uam.persistence", name = "type", havingValue = "mongo", matchIfMissing = true)
class UserCredentialMongoRepositoryAdapter implements UserCredentialRepository {
    private final UserCredentialMongoSpringRepository springRepository;

    UserCredentialMongoRepositoryAdapter(UserCredentialMongoSpringRepository springRepository) {
        this.springRepository = springRepository;
    }

    @Override
    public UserCredential save(UserCredential credential) {
        return toDomain(springRepository.save(toDocument(credential)));
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

    private UserCredential toDomain(UserCredentialDocument doc) {
        return UserCredential.builder()
                .id(doc.getId())
                .userId(doc.getUserId())
                .provider(doc.getProvider())
                .providerUserId(doc.getProviderUserId())
                .passwordHash(doc.getPasswordHash())
                .passwordSalt(doc.getPasswordSalt())
                .temporaryPasswordHash(doc.getTemporaryPasswordHash())
                .temporaryPasswordExpiry(doc.getTemporaryPasswordExpiry())
                .passwordChangedAt(doc.getPasswordChangedAt())
                .passwordExpiryAt(doc.getPasswordExpiryAt())
                .forcePasswordChange(doc.getForcePasswordChange())
                .primaryCredential(doc.getPrimaryCredential())
                .verified(doc.getVerified())
                .emailVerified(doc.getEmailVerified())
                .phoneVerified(doc.getPhoneVerified())
                .refreshToken(doc.getRefreshToken())
                .accessToken(doc.getAccessToken())
                .tokenExpiry(doc.getTokenExpiry())
                .lastAuthenticatedAt(doc.getLastAuthenticatedAt())
                .lastSyncAt(doc.getLastSyncAt())
                .metadata(doc.getMetadata())
                .active(doc.getActive())
                .createdAt(doc.getCreatedAt())
                .createdBy(doc.getCreatedBy())
                .updatedAt(doc.getUpdatedAt())
                .updatedBy(doc.getUpdatedBy())
                .version(doc.getVersion())
                .build();
    }

    private UserCredentialDocument toDocument(UserCredential credential) {
        return UserCredentialDocument.builder()
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
