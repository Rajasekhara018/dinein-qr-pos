package com.heuristiq.uam.passwordsHistory.repository.jpa;

import com.heuristiq.uam.passwordsHistory.PasswordHistory;
import com.heuristiq.uam.passwordsHistory.persistance.jpa.PasswordHistoryEntity;
import com.heuristiq.uam.passwordsHistory.PasswordHistoryRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(prefix = "railcab.uam.persistence", name = "type", havingValue = "postgres")
class PasswordHistoryJpaRepositoryAdapter implements PasswordHistoryRepository {
    private final PasswordHistoryJpaSpringRepository springRepository;

    PasswordHistoryJpaRepositoryAdapter(PasswordHistoryJpaSpringRepository springRepository) {
        this.springRepository = springRepository;
    }

    @Override
    public PasswordHistory save(PasswordHistory passwordHistory) {
        return toDomain(springRepository.save(toEntity(passwordHistory)));
    }

    @Override
    public Page<PasswordHistory> findByUserId(String userId, Pageable pageable) {
        return springRepository.findByUserId(userId, pageable).map(this::toDomain);
    }

    private PasswordHistory toDomain(PasswordHistoryEntity entity) {
        return PasswordHistory.builder()
                .id(entity.getId())
                .userId(entity.getUserId())
                .credentialId(entity.getCredentialId())
                .passwordHash(entity.getPasswordHash())
                .algorithm(entity.getAlgorithm())
                .passwordVersion(entity.getPasswordVersion())
                .changeReason(entity.getChangeReason())
                .changedBy(entity.getChangedBy())
                .passwordExpiryAt(entity.getPasswordExpiryAt())
                .audit(entity.getAudit())
                .build();
    }

    private PasswordHistoryEntity toEntity(PasswordHistory passwordHistory) {
        return PasswordHistoryEntity.builder()
                .id(passwordHistory.getId())
                .userId(passwordHistory.getUserId())
                .credentialId(passwordHistory.getCredentialId())
                .passwordHash(passwordHistory.getPasswordHash())
                .algorithm(passwordHistory.getAlgorithm())
                .passwordVersion(passwordHistory.getPasswordVersion())
                .changeReason(passwordHistory.getChangeReason())
                .changedBy(passwordHistory.getChangedBy())
                .passwordExpiryAt(passwordHistory.getPasswordExpiryAt())
                .audit(passwordHistory.getAudit())
                .build();
    }
}
