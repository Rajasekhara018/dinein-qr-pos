package com.railcab.nms.adapters.out.persistence.jpa;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.railcab.nms.domain.model.AuditOutboxStatus;

interface AuditOutboxJpaSpringRepository extends JpaRepository<AuditOutboxEntity, String> {

    boolean existsByEventId(String eventId);

    @Query("select e from AuditOutboxEntity e where e.status = :status and e.nextAttemptAt <= :now "
            + "and (e.lockedUntil is null or e.lockedUntil < :now) order by e.createdAt asc")
    List<AuditOutboxEntity> findDue(@Param("status") AuditOutboxStatus status, @Param("now") LocalDateTime now,
            Pageable pageable);

    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("update AuditOutboxEntity e set e.lockedUntil = :until, e.lockOwner = :owner "
            + "where e.id = :id and e.status = :status and (e.lockedUntil is null or e.lockedUntil < :now)")
    int lease(@Param("id") String id, @Param("owner") String owner, @Param("now") LocalDateTime now,
            @Param("until") LocalDateTime until, @Param("status") AuditOutboxStatus status);

    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("update AuditOutboxEntity e set e.status = :status, e.sentAt = :sentAt, e.lockedUntil = null, "
            + "e.lockOwner = null, e.lastError = null where e.id = :id")
    int markSent(@Param("id") String id, @Param("sentAt") LocalDateTime sentAt, @Param("status") AuditOutboxStatus status);

    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("update AuditOutboxEntity e set e.status = :status, e.attempts = :attempts, e.nextAttemptAt = :next, "
            + "e.lastError = :error, e.lockedUntil = null, e.lockOwner = null where e.id = :id")
    int markFailed(@Param("id") String id, @Param("attempts") int attempts, @Param("next") LocalDateTime next,
            @Param("error") String error, @Param("status") AuditOutboxStatus status);

    long countByStatus(AuditOutboxStatus status);

    @Transactional
    long deleteByExpireAtBefore(LocalDateTime cutoff);
}
