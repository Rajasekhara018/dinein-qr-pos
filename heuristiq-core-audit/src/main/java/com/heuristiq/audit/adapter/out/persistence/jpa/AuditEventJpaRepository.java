package com.railcab.audit.adapter.out.persistence.jpa;

import com.railcab.audit.domain.AppChannel;
import com.railcab.audit.domain.AuditEventType;
import com.railcab.audit.domain.AuditOutcome;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/** Spring Data access for {@link AuditEventEntity}; used only by {@link AuditEventJpaAdapter}. */
public interface AuditEventJpaRepository
        extends JpaRepository<AuditEventEntity, Long>, JpaSpecificationExecutor<AuditEventEntity> {

    /** @return which of the given event ids are already stored */
    @Query("select e.eventId from AuditEventEntity e where e.eventId in :ids")
    Set<String> findExistingEventIds(@Param("ids") Collection<String> ids);

    /** @return the chain of one correlation id within the organization scope, oldest first; tenantId is never null (use an empty string) */
    @Query("select e from AuditEventEntity e where e.correlationId = :correlationId"
            + " and (:unrestricted = true or e.tenantId is null or e.tenantId = :tenantId)"
            + " order by e.occurredAt asc, e.id asc")
    List<AuditEventEntity> findChain(@Param("correlationId") String correlationId,
                                     @Param("unrestricted") boolean unrestricted, @Param("tenantId") String tenantId,
                                     Pageable pageable);

    /** @return distinct event types since the instant */
    @Query("select distinct e.eventType from AuditEventEntity e where e.occurredAt >= :since and (:unrestricted = true or e.tenantId is null or e.tenantId = :tenantId)")
    List<AuditEventType> distinctEventTypes(@Param("since") Instant since, @Param("unrestricted") boolean unrestricted, @Param("tenantId") String tenantId);

    /** @return distinct features since the instant */
    @Query("select distinct e.feature from AuditEventEntity e where e.feature is not null and e.occurredAt >= :since and (:unrestricted = true or e.tenantId is null or e.tenantId = :tenantId) order by e.feature")
    List<String> distinctFeatures(@Param("since") Instant since, @Param("unrestricted") boolean unrestricted, @Param("tenantId") String tenantId, Pageable pageable);

    /** @return distinct screen keys since the instant */
    @Query("select distinct e.screenKey from AuditEventEntity e where e.screenKey is not null and e.occurredAt >= :since and (:unrestricted = true or e.tenantId is null or e.tenantId = :tenantId) order by e.screenKey")
    List<String> distinctScreenKeys(@Param("since") Instant since, @Param("unrestricted") boolean unrestricted, @Param("tenantId") String tenantId, Pageable pageable);

    /** @return distinct subject types since the instant */
    @Query("select distinct e.subjectType from AuditEventEntity e where e.subjectType is not null and e.occurredAt >= :since and (:unrestricted = true or e.tenantId is null or e.tenantId = :tenantId) order by e.subjectType")
    List<String> distinctSubjectTypes(@Param("since") Instant since, @Param("unrestricted") boolean unrestricted, @Param("tenantId") String tenantId, Pageable pageable);

    /** @return distinct reason codes since the instant */
    @Query("select distinct e.reasonCode from AuditEventEntity e where e.reasonCode is not null and e.occurredAt >= :since and (:unrestricted = true or e.tenantId is null or e.tenantId = :tenantId) order by e.reasonCode")
    List<String> distinctReasonCodes(@Param("since") Instant since, @Param("unrestricted") boolean unrestricted, @Param("tenantId") String tenantId, Pageable pageable);

    /** @return distinct outcomes since the instant */
    @Query("select distinct e.outcome from AuditEventEntity e where e.outcome is not null and e.occurredAt >= :since and (:unrestricted = true or e.tenantId is null or e.tenantId = :tenantId)")
    List<AuditOutcome> distinctOutcomes(@Param("since") Instant since, @Param("unrestricted") boolean unrestricted, @Param("tenantId") String tenantId);

    /** @return distinct channels since the instant */
    @Query("select distinct e.appChannel from AuditEventEntity e where e.appChannel is not null and e.occurredAt >= :since and (:unrestricted = true or e.tenantId is null or e.tenantId = :tenantId)")
    List<AppChannel> distinctChannels(@Param("since") Instant since, @Param("unrestricted") boolean unrestricted, @Param("tenantId") String tenantId);

    /** @return every individual role seen since the instant (unnests the delimited roles column) */
    @Query(value = "select distinct r from audit_events e, unnest(string_to_array(trim(both ',' from e.roles), ',')) as r "
            + "where e.roles is not null and e.occurred_at >= :since and (:unrestricted = true or e.tenant_id is null or e.tenant_id = :tenantId) and r <> '' order by r limit :max", nativeQuery = true)
    List<String> distinctRoles(@Param("since") Instant since, @Param("unrestricted") boolean unrestricted, @Param("tenantId") String tenantId, @Param("max") int max);

    /** Deletes one batch of expired rows. */
    @Transactional
    @Modifying
    @Query(value = "delete from audit_events where id in (select id from audit_events where expire_at < :now limit :batch)",
            nativeQuery = true)
    int deleteExpiredBatch(@Param("now") Instant now, @Param("batch") int batch);
}
