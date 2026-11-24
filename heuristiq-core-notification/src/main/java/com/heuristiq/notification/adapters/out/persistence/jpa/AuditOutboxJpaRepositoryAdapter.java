package com.railcab.nms.adapters.out.persistence.jpa;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import com.railcab.nms.domain.model.AuditOutboxEntry;
import com.railcab.nms.domain.model.AuditOutboxStatus;
import com.railcab.nms.domain.repository.IAuditOutboxRepository;

/** JPA (MySQL/Postgres) implementation of {@link IAuditOutboxRepository}. */
@Repository
@ConditionalOnExpression("'${nms.persistence.type:mongo}' == 'mysql' or '${nms.persistence.type:mongo}' == 'postgres'")
class AuditOutboxJpaRepositoryAdapter implements IAuditOutboxRepository {

    private final AuditOutboxJpaSpringRepository springRepository;

    AuditOutboxJpaRepositoryAdapter(AuditOutboxJpaSpringRepository springRepository) {
        this.springRepository = springRepository;
    }

    @Override
    public boolean insertIfAbsent(AuditOutboxEntry entry) {
        if (springRepository.existsByEventId(entry.getEventId())) {
            return false;
        }
        try {
            springRepository.saveAndFlush(toEntity(entry));
            return true;
        } catch (DataIntegrityViolationException duplicate) {
            // Another instance queued the same deterministic event between the check and the insert.
            return false;
        }
    }

    @Override
    public List<AuditOutboxEntry> findDue(LocalDateTime now, int limit) {
        return springRepository.findDue(AuditOutboxStatus.PENDING, now, PageRequest.of(0, limit)).stream()
                .map(this::toDomain).toList();
    }

    @Override
    public boolean tryLease(String id, String owner, LocalDateTime now, LocalDateTime leaseUntil) {
        return springRepository.lease(id, owner, now, leaseUntil, AuditOutboxStatus.PENDING) == 1;
    }

    @Override
    public void markSent(String id, LocalDateTime sentAt) {
        springRepository.markSent(id, sentAt, AuditOutboxStatus.SENT);
    }

    @Override
    public void markFailed(String id, int attempts, LocalDateTime nextAttemptAt, String error, boolean dead) {
        springRepository.markFailed(id, attempts, nextAttemptAt, cut(error),
                dead ? AuditOutboxStatus.DEAD : AuditOutboxStatus.PENDING);
    }

    @Override
    public long countByStatus(AuditOutboxStatus status) {
        return springRepository.countByStatus(status);
    }

    @Override
    public void purgeExpired(LocalDateTime now) {
        springRepository.deleteByExpireAtBefore(now);
    }

    private static String cut(String value) {
        if (value == null || value.length() <= JpaPersistenceConstants.AUDIT_OUTBOX_ERROR_COLUMN_LENGTH) {
            return value;
        }
        return value.substring(0, JpaPersistenceConstants.AUDIT_OUTBOX_ERROR_COLUMN_LENGTH);
    }

    private AuditOutboxEntry toDomain(AuditOutboxEntity e) {
        return AuditOutboxEntry.builder()
                .id(e.getId())
                .eventId(e.getEventId())
                .payload(e.getPayload())
                .status(e.getStatus())
                .attempts(e.getAttempts())
                .nextAttemptAt(e.getNextAttemptAt())
                .lockedUntil(e.getLockedUntil())
                .lockOwner(e.getLockOwner())
                .lastError(e.getLastError())
                .createdAt(e.getCreatedAt())
                .sentAt(e.getSentAt())
                .expireAt(e.getExpireAt())
                .build();
    }

    private AuditOutboxEntity toEntity(AuditOutboxEntry e) {
        return AuditOutboxEntity.builder()
                .id(e.getId())
                .eventId(e.getEventId())
                .payload(e.getPayload())
                .status(e.getStatus())
                .attempts(e.getAttempts())
                .nextAttemptAt(e.getNextAttemptAt())
                .lockedUntil(e.getLockedUntil())
                .lockOwner(e.getLockOwner())
                .lastError(e.getLastError())
                .createdAt(e.getCreatedAt())
                .sentAt(e.getSentAt())
                .expireAt(e.getExpireAt())
                .build();
    }
}
