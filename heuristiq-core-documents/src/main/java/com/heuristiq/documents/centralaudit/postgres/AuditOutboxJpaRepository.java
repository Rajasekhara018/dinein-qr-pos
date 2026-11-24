package com.heuristiq.documents.centralaudit.postgres;

import com.heuristiq.documents.centralaudit.OutboxStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

interface AuditOutboxJpaRepository extends JpaRepository<AuditOutboxJpaEntity, String> {

    boolean existsByEventId(String eventId);

    @Query("select e from AuditOutboxJpaEntity e where e.status = :status and e.nextAttemptAt <= :now "
            + "and (e.lockedUntil is null or e.lockedUntil < :now) order by e.createdAt asc")
    List<AuditOutboxJpaEntity> findDue(@Param("status") OutboxStatus status, @Param("now") Instant now, Pageable pageable);

    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("update AuditOutboxJpaEntity e set e.lockedUntil = :until, e.lockOwner = :owner "
            + "where e.id = :id and e.status = :status and (e.lockedUntil is null or e.lockedUntil < :now)")
    int lease(@Param("id") String id, @Param("owner") String owner, @Param("now") Instant now,
              @Param("until") Instant until, @Param("status") OutboxStatus status);

    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("update AuditOutboxJpaEntity e set e.status = :status, e.sentAt = :sentAt, e.lockedUntil = null, "
            + "e.lockOwner = null, e.lastError = null where e.id = :id")
    int markSent(@Param("id") String id, @Param("sentAt") Instant sentAt, @Param("status") OutboxStatus status);

    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("update AuditOutboxJpaEntity e set e.status = :status, e.attempts = :attempts, e.nextAttemptAt = :next, "
            + "e.lastError = :error, e.lockedUntil = null, e.lockOwner = null where e.id = :id")
    int markFailed(@Param("id") String id, @Param("attempts") int attempts, @Param("next") Instant next,
                   @Param("error") String error, @Param("status") OutboxStatus status);

    long countByStatus(OutboxStatus status);

    @Transactional
    long deleteByExpireAtBefore(Instant cutoff);
}
