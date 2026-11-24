package com.railcab.dms.centralaudit.postgres;

import com.railcab.dms.common.DmsCommonConstants;
import com.railcab.dms.centralaudit.AuditOutboxStore;
import com.railcab.dms.centralaudit.CentralAuditConstants;
import com.railcab.dms.centralaudit.OutboxEntry;
import com.railcab.dms.centralaudit.OutboxStatus;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** PostgreSQL adapter of the audit outbox. */
@Component
@ConditionalOnProperty(prefix = DmsCommonConstants.PERSISTENCE_PROPERTY_PREFIX, name = DmsCommonConstants.PERSISTENCE_PROVIDER_PROPERTY, havingValue = DmsCommonConstants.PERSISTENCE_POSTGRES)
public class PostgresAuditOutboxStore implements AuditOutboxStore {

    private final AuditOutboxJpaRepository repository;

    public PostgresAuditOutboxStore(AuditOutboxJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public boolean insertIfAbsent(OutboxEntry entry) {
        if (repository.existsByEventId(entry.eventId())) {
            return false;
        }
        try {
            repository.saveAndFlush(AuditOutboxJpaEntity.fromDomain(entry, UUID.randomUUID().toString()));
            return true;
        } catch (DataIntegrityViolationException duplicate) {
            // Another instance queued the same event between the check and the insert.
            return false;
        }
    }

    @Override
    public List<OutboxEntry> findDue(Instant now, int limit) {
        return repository.findDue(OutboxStatus.PENDING, now, PageRequest.of(0, limit)).stream()
                .map(AuditOutboxJpaEntity::toDomain).toList();
    }

    @Override
    public boolean tryLease(String id, String owner, Instant now, Instant leaseUntil) {
        return repository.lease(id, owner, now, leaseUntil, OutboxStatus.PENDING) == 1;
    }

    @Override
    public void markSent(String id, Instant sentAt) {
        repository.markSent(id, sentAt, OutboxStatus.SENT);
    }

    @Override
    public void markFailed(String id, int attempts, Instant nextAttemptAt, String error, boolean dead) {
        String cut = error != null && error.length() > CentralAuditConstants.ERROR_COLUMN_LENGTH
                ? error.substring(0, CentralAuditConstants.ERROR_COLUMN_LENGTH) : error;
        repository.markFailed(id, attempts, nextAttemptAt, cut, dead ? OutboxStatus.DEAD : OutboxStatus.PENDING);
    }

    @Override
    public long countByStatus(OutboxStatus status) {
        return repository.countByStatus(status);
    }

    @Override
    public void purgeExpired(Instant now) {
        repository.deleteByExpireAtBefore(now);
    }
}
