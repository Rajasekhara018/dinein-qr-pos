package com.heuristiq.identity.mfa.repository.jpa;

import com.heuristiq.identity.enums.UamEnums.MfaType;
import com.heuristiq.identity.mfa.UserMfa;
import com.heuristiq.identity.mfa.persistence.jpa.UserMfaEntity;
import com.heuristiq.identity.mfa.repository.UserMfaRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@ConditionalOnProperty(prefix = "railcab.uam.persistence", name = "type", havingValue = "postgres")
class UserMfaJpaRepositoryAdapter implements UserMfaRepository {
    private final UserMfaJpaSpringRepository springRepository;

    UserMfaJpaRepositoryAdapter(UserMfaJpaSpringRepository springRepository) {
        this.springRepository = springRepository;
    }

    @Override
    public UserMfa save(UserMfa mfa) {
        return toDomain(springRepository.save(toEntity(mfa)));
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

    private UserMfa toDomain(UserMfaEntity entity) {
        return UserMfa.builder()
                .id(entity.getId())
                .userId(entity.getUserId())
                .mfaType(entity.getMfaType())
                .secret(entity.getSecret())
                .qrCodeUrl(entity.getQrCodeUrl())
                .backupCodes(entity.getBackupCodes())
                .phoneNumber(entity.getPhoneNumber())
                .email(entity.getEmail())
                .enabled(entity.getEnabled())
                .primary(entity.getPrimary())
                .verified(entity.getVerified())
                .lastVerifiedAt(entity.getLastVerifiedAt())
                .failedAttempts(entity.getFailedAttempts())
                .locked(entity.getLocked())
                .lockedUntil(entity.getLockedUntil())
                .deviceId(entity.getDeviceId())
                .metadata(entity.getMetadata())
                .active(entity.getActive())
                .createdAt(entity.getCreatedAt())
                .createdBy(entity.getCreatedBy())
                .updatedAt(entity.getUpdatedAt())
                .updatedBy(entity.getUpdatedBy())
                .version(entity.getVersion())
                .build();
    }

    private UserMfaEntity toEntity(UserMfa mfa) {
        return UserMfaEntity.builder()
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
