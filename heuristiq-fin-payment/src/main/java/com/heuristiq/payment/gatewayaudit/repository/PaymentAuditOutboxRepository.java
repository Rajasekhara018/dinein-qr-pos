package com.heuristiq.paymentservice.gatewayaudit.repository;

import com.heuristiq.paymentservice.gatewayaudit.model.PaymentAuditOutboxEntry;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/** Persistence for {@code payment_audit_outbox}. Each method is its own short transaction. */
public interface PaymentAuditOutboxRepository extends JpaRepository<PaymentAuditOutboxEntry, Long> {

    /** Undelivered entries that are due, oldest first. */
    @Query("select e from PaymentAuditOutboxEntry e where e.publishedAt is null and e.nextAttemptAt <= :now order by e.id")
    List<PaymentAuditOutboxEntry> findDue(@Param("now") LocalDateTime now, Pageable pageable);

    @Transactional
    @Modifying
    @Query("update PaymentAuditOutboxEntry e set e.publishedAt = :now where e.id in :ids")
    int markPublished(@Param("ids") Collection<Long> ids, @Param("now") LocalDateTime now);

    @Transactional
    @Modifying
    @Query("update PaymentAuditOutboxEntry e set e.attempts = e.attempts + 1, e.nextAttemptAt = :next where e.id in :ids")
    int markFailed(@Param("ids") Collection<Long> ids, @Param("next") LocalDateTime next);

    @Transactional
    @Modifying
    @Query("delete from PaymentAuditOutboxEntry e where e.publishedAt is not null and e.publishedAt < :cutoff")
    int deletePublishedBefore(@Param("cutoff") LocalDateTime cutoff);

    long countByPublishedAtIsNull();
}
