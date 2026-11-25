package com.heuristiq.identity.passwordsHistory.repository.mongo;

import com.heuristiq.identity.passwordsHistory.PasswordHistory;
import com.heuristiq.identity.passwordsHistory.persistance.mongo.PasswordHistoryDocument;
import com.heuristiq.identity.passwordsHistory.PasswordHistoryRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(prefix = "railcab.uam.persistence", name = "type", havingValue = "mongo", matchIfMissing = true)
class PasswordHistoryMongoRepositoryAdapter implements PasswordHistoryRepository {
    private final PasswordHistoryMongoSpringRepository springRepository;

    PasswordHistoryMongoRepositoryAdapter(PasswordHistoryMongoSpringRepository springRepository) {
        this.springRepository = springRepository;
    }

    @Override
    public PasswordHistory save(PasswordHistory passwordHistory) {
        return toDomain(springRepository.save(toDocument(passwordHistory)));
    }

    @Override
    public Page<PasswordHistory> findByUserId(String userId, Pageable pageable) {
        return springRepository.findByUserId(userId, pageable).map(this::toDomain);
    }

    private PasswordHistory toDomain(PasswordHistoryDocument doc) {
        return PasswordHistory.builder()
                .id(doc.getId())
                .userId(doc.getUserId())
                .credentialId(doc.getCredentialId())
                .passwordHash(doc.getPasswordHash())
                .algorithm(doc.getAlgorithm())
                .passwordVersion(doc.getPasswordVersion())
                .changeReason(doc.getChangeReason())
                .changedBy(doc.getChangedBy())
                .passwordExpiryAt(doc.getPasswordExpiryAt())
                .audit(doc.getAudit())
                .build();
    }

    private PasswordHistoryDocument toDocument(PasswordHistory passwordHistory) {
        return PasswordHistoryDocument.builder()
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
