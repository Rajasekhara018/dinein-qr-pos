package com.railcab.audit.adapter.out.persistence.mongo;

import static com.railcab.audit.adapter.out.persistence.PersistenceConstants.*;
import com.mongodb.bulk.BulkWriteError;
import com.railcab.audit.domain.PageResult;
import com.railcab.audit.domain.AuditEvent;
import com.railcab.audit.domain.AuditEventFilter;
import com.railcab.audit.domain.AuditFacets;
import com.railcab.audit.domain.AuditPayload;
import com.railcab.audit.domain.TenantScope;
import com.railcab.audit.domain.AuditEventRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.BulkOperationException;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * MongoDB implementation of {@link AuditEventRepository}; the default
 * ({@code railcab.audit.persistence.type=mongo}). Inserts are unordered bulk writes that tolerate duplicate-key
 * errors on the unique {@code eventId} index, so a retried batch stores each event once.
 */
@Repository
@ConditionalOnProperty(prefix = "railcab.audit.persistence", name = "type", havingValue = "mongo", matchIfMissing = true)
public class AuditEventMongoAdapter implements AuditEventRepository {

    private final MongoTemplate mongo;
    private final MongoSequence sequence;

    /**
     * Creates the adapter.
     *
     * @param mongo    Mongo access
     * @param sequence id sequence
     */
    public AuditEventMongoAdapter(MongoTemplate mongo, MongoSequence sequence) {
        this.mongo = mongo;
        this.sequence = sequence;
    }

    @Override
    public List<String> insertNew(List<AuditEvent> events) {
        if (events.isEmpty()) {
            return List.of();
        }
        Set<String> existing = existingEventIds(events.stream().map(AuditEvent::getEventId).toList());
        List<AuditEventDocument> fresh = new ArrayList<>();
        for (AuditEvent e : events) {
            if (!existing.contains(e.getEventId())) {
                AuditEventDocument d = AuditEventDocument.from(e);
                d.setId(sequence.next(AUDIT_EVENTS_COLLECTION));
                fresh.add(d);
            }
        }
        if (fresh.isEmpty()) {
            return List.of();
        }
        try {
            mongo.bulkOps(BulkOperations.BulkMode.UNORDERED, AuditEventDocument.class).insert(fresh).execute();
            return fresh.stream().map(AuditEventDocument::getEventId).toList();
        } catch (BulkOperationException e) {
            Set<Integer> failed = new HashSet<>();
            for (BulkWriteError error : e.getErrors()) {
                if (error.getCode() != DUPLICATE_KEY) {
                    throw e;
                }
                failed.add(error.getIndex());
            }
            List<String> inserted = new ArrayList<>();
            for (int i = 0; i < fresh.size(); i++) {
                if (!failed.contains(i)) {
                    inserted.add(fresh.get(i).getEventId());
                }
            }
            return inserted;
        }
    }

    private Set<String> existingEventIds(Collection<String> ids) {
        Query q = new Query(Criteria.where(FIELD_EVENT_ID).in(ids));
        q.fields().include(FIELD_EVENT_ID);
        Set<String> found = new HashSet<>();
        mongo.find(q, AuditEventDocument.class).forEach(d -> found.add(d.getEventId()));
        return found;
    }

    @Override
    public void insertPayloads(List<AuditPayload> payloads) {
        for (AuditPayload p : payloads) {
            try {
                mongo.insert(AuditPayloadDocument.from(p));
            } catch (DuplicateKeyException duplicate) {
                // already stored, e.g. by a retry
            }
        }
    }

    @Override
    public Optional<AuditPayload> findPayload(String eventId) {
        return Optional.ofNullable(mongo.findById(eventId, AuditPayloadDocument.class)).map(AuditPayloadDocument::toDomain);
    }

    @Override
    public Map<String, AuditPayload> findPayloads(Collection<String> eventIds) {
        Map<String, AuditPayload> out = new HashMap<>();
        mongo.find(new Query(Criteria.where(FIELD_MONGO_ID).in(eventIds)), AuditPayloadDocument.class)
                .forEach(d -> out.put(d.getEventId(), d.toDomain()));
        return out;
    }

    @Override
    public Optional<AuditEvent> findById(long id) {
        return Optional.ofNullable(mongo.findById(id, AuditEventDocument.class)).map(AuditEventDocument::toDomain);
    }

    @Override
    public PageResult<AuditEvent> search(AuditEventFilter filter, int page, int size) {
        Query base = query(filter);
        long total = mongo.count(Query.of(base).limit(-1).skip(-1), AuditEventDocument.class);
        Pageable pageable = PageRequest.of(page, size, NEWEST_FIRST);
        List<AuditEvent> content = mongo.find(Query.of(base).with(pageable), AuditEventDocument.class).stream()
                .map(AuditEventDocument::toDomain).toList();
        return PageResult.of(content, total, page, size);
    }

    @Override
    public List<AuditEvent> searchForExport(AuditEventFilter filter, int max) {
        return mongo.find(query(filter).with(NEWEST_FIRST).limit(max), AuditEventDocument.class).stream()
                .map(AuditEventDocument::toDomain).toList();
    }

    @Override
    public List<AuditEvent> findByCorrelationId(String correlationId, int max, TenantScope scope) {
        List<Criteria> all = new ArrayList<>();
        all.add(Criteria.where(FIELD_CORRELATION_ID).is(correlationId));
        addTenant(all, scope);
        Query q = new Query(new Criteria().andOperator(all))
                .with(Sort.by(Sort.Direction.ASC, FIELD_OCCURRED_AT, FIELD_ID)).limit(max);
        return mongo.find(q, AuditEventDocument.class).stream().map(AuditEventDocument::toDomain).toList();
    }

    @Override
    public AuditFacets facets(Instant since, int maxValues, TenantScope scope) {
        List<Criteria> all = new ArrayList<>();
        all.add(Criteria.where(FIELD_OCCURRED_AT).gte(since));
        addTenant(all, scope);
        Query recent = new Query(new Criteria().andOperator(all));
        return new AuditFacets(distinct(recent, FIELD_EVENT_TYPE, maxValues), distinct(recent, FIELD_ROLES, maxValues),
                distinct(recent, FIELD_FEATURE, maxValues), distinct(recent, FIELD_SCREEN_KEY, maxValues),
                distinct(recent, FIELD_OUTCOME, maxValues), distinct(recent, FIELD_APP_CHANNEL, maxValues),
                distinct(recent, FIELD_SUBJECT_TYPE, maxValues), distinct(recent, FIELD_REASON_CODE, maxValues));
    }

    private List<String> distinct(Query q, String field, int max) {
        return mongo.findDistinct(Query.of(q), field, AuditEventDocument.class, String.class).stream()
                .filter(v -> v != null && !v.isEmpty()).sorted().limit(max).toList();
    }

    @Override
    public long deleteExpired(Instant now) {
        mongo.remove(new Query(Criteria.where(FIELD_EXPIRE_AT).lt(now)), AuditPayloadDocument.class);
        return mongo.remove(new Query(Criteria.where(FIELD_EXPIRE_AT).lt(now)), AuditEventDocument.class).getDeletedCount();
    }

    private static Query query(AuditEventFilter f) {
        List<Criteria> all = new ArrayList<>();
        if (f.userId() != null) {
            all.add(Criteria.where(FIELD_USER_ID).is(f.userId()));
        }
        if (f.role() != null) {
            all.add(new Criteria().orOperator(Criteria.where(FIELD_ROLE).is(f.role()), Criteria.where(FIELD_ROLES).is(f.role())));
        }
        if (f.feature() != null) {
            all.add(Criteria.where(FIELD_FEATURE).is(f.feature()));
        }
        if (f.screenKey() != null) {
            all.add(Criteria.where(FIELD_SCREEN_KEY).is(f.screenKey()));
        }
        if (f.eventType() != null) {
            all.add(Criteria.where(FIELD_EVENT_TYPE).is(f.eventType().name()));
        }
        if (f.outcome() != null) {
            all.add(Criteria.where(FIELD_OUTCOME).is(f.outcome().name()));
        }
        if (f.channel() != null) {
            all.add(Criteria.where(FIELD_APP_CHANNEL).is(f.channel().name()));
        }
        if (f.privilegeId() != null) {
            all.add(Criteria.where(FIELD_PRIVILEGE_IDS).is(f.privilegeId()));
        }
        if (f.method() != null) {
            all.add(Criteria.where(FIELD_METHOD).is(f.method().toUpperCase(Locale.ROOT)));
        }
        if (f.pathContains() != null) {
            all.add(Criteria.where(FIELD_PATH).regex(Pattern.compile(Pattern.quote(f.pathContains()), Pattern.CASE_INSENSITIVE)));
        }
        if (f.correlationId() != null) {
            all.add(Criteria.where(FIELD_CORRELATION_ID).is(f.correlationId()));
        }
        if (f.subjectType() != null) {
            all.add(Criteria.where(FIELD_SUBJECT_TYPE).is(f.subjectType()));
        }
        if (f.subjectId() != null) {
            all.add(Criteria.where(FIELD_SUBJECT_ID).is(f.subjectId()));
        }
        if (f.reasonCode() != null) {
            all.add(Criteria.where(FIELD_REASON_CODE).is(f.reasonCode()));
        }
        if (f.from() != null || f.to() != null) {
            Criteria time = Criteria.where(FIELD_OCCURRED_AT);
            if (f.from() != null) {
                time = time.gte(f.from());
            }
            if (f.to() != null) {
                time = time.lt(f.to());
            }
            all.add(time);
        }
        addTenant(all, f.tenantScope());
        return all.isEmpty() ? new Query() : new Query(new Criteria().andOperator(all));
    }

    /** Adds the organization limit: own organization plus unowned records, or unowned records only. */
    private static void addTenant(List<Criteria> all, TenantScope scope) {
        if (scope == null || scope.unrestricted()) {
            return;
        }
        Criteria unowned = Criteria.where(FIELD_TENANT_ID).is(null);
        all.add(scope.hasTenant()
                ? new Criteria().orOperator(unowned, Criteria.where(FIELD_TENANT_ID).is(scope.tenantId()))
                : unowned);
    }
}
