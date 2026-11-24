package com.railcab.audit.adapter.out.persistence.jpa;

import static com.railcab.audit.adapter.out.persistence.PersistenceConstants.*;
import com.railcab.audit.domain.AuditEventRepository;
import com.railcab.audit.domain.PageResult;
import com.railcab.audit.domain.AuditEvent;
import com.railcab.audit.domain.AuditEventFilter;
import com.railcab.audit.domain.AuditFacets;
import com.railcab.audit.domain.AuditPayload;
import com.railcab.audit.domain.TenantScope;
import jakarta.persistence.criteria.Predicate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * JPA implementation of {@link AuditEventRepository}; active when {@code railcab.audit.persistence.type=postgres}.
 * Inserts are batched and idempotent on {@code event_id}: {@link AuditEventBatchWriter} sends multi-row
 * {@code INSERT ... ON CONFLICT (event_id) DO NOTHING} statements, so an event or payload already stored (a retry, or
 * another instance storing it at the same moment) is skipped by the database. Each call is one transaction.
 */
@Repository
@ConditionalOnProperty(prefix = "railcab.audit.persistence", name = "type", havingValue = "postgres")
public class AuditEventJpaAdapter implements AuditEventRepository {

    private final AuditEventJpaRepository jpa;
    private final AuditPayloadJpaRepository payloads;
    private final AuditEventBatchWriter writer;

    /**
     * Creates the adapter.
     *
     * @param jpa      the Spring Data repository of events
     * @param payloads the Spring Data repository of payloads
     * @param writer   the batched, duplicate-skipping insert of events and payloads
     */
    public AuditEventJpaAdapter(AuditEventJpaRepository jpa, AuditPayloadJpaRepository payloads,
            AuditEventBatchWriter writer) {
        this.jpa = jpa;
        this.payloads = payloads;
        this.writer = writer;
    }

    @Override
    @Transactional
    public List<String> insertNew(List<AuditEvent> events) {
        if (events.isEmpty()) {
            return List.of();
        }
        return writer.insertEvents(events);
    }

    @Override
    @Transactional
    public void insertPayloads(List<AuditPayload> toStore) {
        if (!toStore.isEmpty()) {
            writer.insertPayloads(toStore);
        }
    }

    @Override
    public Optional<AuditPayload> findPayload(String eventId) {
        return payloads.findById(eventId).map(AuditPayloadEntity::toDomain);
    }

    @Override
    public Map<String, AuditPayload> findPayloads(Collection<String> eventIds) {
        Map<String, AuditPayload> out = new HashMap<>();
        payloads.findAllById(eventIds).forEach(e -> out.put(e.getEventId(), e.toDomain()));
        return out;
    }

    @Override
    public Optional<AuditEvent> findById(long id) {
        return jpa.findById(id).map(AuditEventEntity::toDomain);
    }

    @Override
    public PageResult<AuditEvent> search(AuditEventFilter filter, int page, int size) {
        Page<AuditEventEntity> found = jpa.findAll(spec(filter), PageRequest.of(page, size, NEWEST_FIRST));
        return PageResult.of(found.getContent().stream().map(AuditEventEntity::toDomain).toList(),
                found.getTotalElements(), page, size);
    }

    @Override
    public List<AuditEvent> searchForExport(AuditEventFilter filter, int max) {
        return jpa.findAll(spec(filter), PageRequest.of(0, max, NEWEST_FIRST)).stream()
                .map(AuditEventEntity::toDomain).toList();
    }

    @Override
    public List<AuditEvent> findByCorrelationId(String correlationId, int max, TenantScope scope) {
        return jpa.findChain(correlationId, scope.unrestricted(), tenantParam(scope), PageRequest.of(0, max)).stream()
                .map(AuditEventEntity::toDomain).toList();
    }

    @Override
    public AuditFacets facets(Instant since, int maxValues, TenantScope scope) {
        Pageable cap = PageRequest.of(0, maxValues);
        boolean all = scope.unrestricted();
        String tenant = tenantParam(scope);
        return new AuditFacets(
                names(jpa.distinctEventTypes(since, all, tenant)),
                jpa.distinctRoles(since, all, tenant, maxValues),
                jpa.distinctFeatures(since, all, tenant, cap),
                jpa.distinctScreenKeys(since, all, tenant, cap),
                names(jpa.distinctOutcomes(since, all, tenant)),
                names(jpa.distinctChannels(since, all, tenant)),
                jpa.distinctSubjectTypes(since, all, tenant, cap),
                jpa.distinctReasonCodes(since, all, tenant, cap));
    }

    @Override
    public long deleteExpired(Instant now) {
        int goneOnes;
        do {
            goneOnes = payloads.deleteExpiredBatch(now, PURGE_BATCH);
        } while (goneOnes == PURGE_BATCH);
        long total = 0;
        int removed;
        do {
            removed = jpa.deleteExpiredBatch(now, PURGE_BATCH);
            total += removed;
        } while (removed == PURGE_BATCH);
        return total;
    }

    /** The bound organization; empty (never a real id, blank ids are stored as null) when the reader has none. */
    private static String tenantParam(TenantScope scope) {
        return scope.hasTenant() ? scope.tenantId() : "";
    }

    private static List<String> names(List<? extends Enum<?>> values) {
        return values.stream().map(Enum::name).sorted().toList();
    }

    private static Specification<AuditEventEntity> spec(AuditEventFilter f) {
        return (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (f.userId() != null) {
                p.add(cb.equal(root.get(FIELD_USER_ID), f.userId()));
            }
            if (f.role() != null) {
                p.add(cb.or(cb.equal(root.get(FIELD_ROLE), f.role()),
                        cb.like(root.get(FIELD_ROLES), "%," + escapeLike(f.role()) + ",%", '\\')));
            }
            if (f.feature() != null) {
                p.add(cb.equal(root.get(FIELD_FEATURE), f.feature()));
            }
            if (f.screenKey() != null) {
                p.add(cb.equal(root.get(FIELD_SCREEN_KEY), f.screenKey()));
            }
            if (f.eventType() != null) {
                p.add(cb.equal(root.get(FIELD_EVENT_TYPE), f.eventType()));
            }
            if (f.outcome() != null) {
                p.add(cb.equal(root.get(FIELD_OUTCOME), f.outcome()));
            }
            if (f.channel() != null) {
                p.add(cb.equal(root.get(FIELD_APP_CHANNEL), f.channel()));
            }
            if (f.privilegeId() != null) {
                p.add(cb.like(root.get(FIELD_PRIVILEGE_IDS), "%," + f.privilegeId() + ",%"));
            }
            if (f.method() != null) {
                p.add(cb.equal(root.get(FIELD_METHOD), f.method().toUpperCase(Locale.ROOT)));
            }
            if (f.pathContains() != null) {
                p.add(cb.like(cb.lower(root.get(FIELD_PATH)), "%" + escapeLike(f.pathContains().toLowerCase(Locale.ROOT)) + "%", '\\'));
            }
            if (f.correlationId() != null) {
                p.add(cb.equal(root.get(FIELD_CORRELATION_ID), f.correlationId()));
            }
            if (f.subjectType() != null) {
                p.add(cb.equal(root.get(FIELD_SUBJECT_TYPE), f.subjectType()));
            }
            if (f.subjectId() != null) {
                p.add(cb.equal(root.get(FIELD_SUBJECT_ID), f.subjectId()));
            }
            if (f.reasonCode() != null) {
                p.add(cb.equal(root.get(FIELD_REASON_CODE), f.reasonCode()));
            }
            if (f.from() != null) {
                p.add(cb.greaterThanOrEqualTo(root.get(FIELD_OCCURRED_AT), f.from()));
            }
            if (f.to() != null) {
                p.add(cb.lessThan(root.get(FIELD_OCCURRED_AT), f.to()));
            }
            TenantScope scope = f.tenantScope();
            if (scope != null && !scope.unrestricted()) {
                p.add(scope.hasTenant()
                        ? cb.or(cb.isNull(root.get(FIELD_TENANT_ID)), cb.equal(root.get(FIELD_TENANT_ID), scope.tenantId()))
                        : cb.isNull(root.get(FIELD_TENANT_ID)));
            }
            return cb.and(p.toArray(new Predicate[0]));
        };
    }

    private static String escapeLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
