package com.railcab.audit.adapter.out.persistence.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/** Spring Data access for {@link AuditPayloadEntity}; used only by {@link AuditEventJpaAdapter}. */
public interface AuditPayloadJpaRepository extends JpaRepository<AuditPayloadEntity, String> {

    /** Deletes one batch of expired payloads. */
    @Transactional
    @Modifying
    @Query(value = "delete from audit_event_payloads where event_id in "
            + "(select event_id from audit_event_payloads where expire_at < :now limit :batch)", nativeQuery = true)
    int deleteExpiredBatch(@Param("now") Instant now, @Param("batch") int batch);
}
