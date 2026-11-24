package com.heuristiq.uam.mfa.repository.mongo;

import com.heuristiq.uam.enums.UamEnums.MfaType;
import com.heuristiq.uam.mfa.UserMfa;
import com.heuristiq.uam.mfa.persistence.mongo.UserMfaDocument;
import com.heuristiq.uam.mfa.repository.UserMfaRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@ConditionalOnProperty(prefix = "railcab.uam.persistence", name = "type", havingValue = "mongo", matchIfMissing = true)
class UserMfaMongoRepositoryAdapter implements UserMfaRepository {
    private final UserMfaMongoSpringRepository springRepository;

    UserMfaMongoRepositoryAdapter(UserMfaMongoSpringRepository springRepository) {
        this.springRepository = springRepository;
    }

    @Override
    public UserMfa save(UserMfa mfa) {
        return toDomain(springRepository.save(toDocument(mfa)));
    }

    @Override
    public Optional<UserMfa> findById(String id) {
        return springRepository.findById(id).map(this::toDomain);
    }

    @Override
    public List<UserMfa> findByUserId(String userId) {
        return springRepository.findByUserId(userId).stream().map(this::toDomain).toList();
    }

    @Override
    public Optional<UserMfa> findByUserIdAndMfaType(String userId, MfaType mfaType) {
        return springRepository.findByUserIdAndMfaType(userId, mfaType).map(this::toDomain);
    }

    @Override
    public void delete(UserMfa mfa) {
        springRepository.deleteById(mfa.getId());
    }

    private UserMfa toDomain(UserMfaDocument doc) {
        return UserMfa.builder()
                .id(doc.getId())
                .userId(doc.getUserId())
                .mfaType(doc.getMfaType())
                .secret(doc.getSecret())
                .qrCodeUrl(doc.getQrCodeUrl())
                .backupCodes(doc.getBackupCodes())
                .phoneNumber(doc.getPhoneNumber())
                .email(doc.getEmail())
                .enabled(doc.getEnabled())
                .primary(doc.getPrimary())
                .verified(doc.getVerified())
                .lastVerifiedAt(doc.getLastVerifiedAt())
                .failedAttempts(doc.getFailedAttempts())
                .locked(doc.getLocked())
                .lockedUntil(doc.getLockedUntil())
                .deviceId(doc.getDeviceId())
                .metadata(doc.getMetadata())
                .active(doc.getActive())
                .createdAt(doc.getCreatedAt())
                .createdBy(doc.getCreatedBy())
                .updatedAt(doc.getUpdatedAt())
                .updatedBy(doc.getUpdatedBy())
                .version(doc.getVersion())
                .build();
    }

    private UserMfaDocument toDocument(UserMfa mfa) {
        return UserMfaDocument.builder()
                .id(mfa.getId())
                .userId(mfa.getUserId())
                .mfaType(mfa.getMfaType())
                .secret(mfa.getSecret())
                .qrCodeUrl(mfa.getQrCodeUrl())
                .backupCodes(mfa.getBackupCodes())
                .phoneNumber(mfa.getPhoneNumber())
                .email(mfa.getEmail())
                .enabled(mfa.getEnabled())
                .primary(mfa.getPrimary())
                .verified(mfa.getVerified())
                .lastVerifiedAt(mfa.getLastVerifiedAt())
                .failedAttempts(mfa.getFailedAttempts())
                .locked(mfa.getLocked())
                .lockedUntil(mfa.getLockedUntil())
                .deviceId(mfa.getDeviceId())
                .metadata(mfa.getMetadata())
                .active(mfa.getActive())
                .createdAt(mfa.getCreatedAt())
                .createdBy(mfa.getCreatedBy())
                .updatedAt(mfa.getUpdatedAt())
                .updatedBy(mfa.getUpdatedBy())
                .version(mfa.getVersion())
                .build();
    }
}
