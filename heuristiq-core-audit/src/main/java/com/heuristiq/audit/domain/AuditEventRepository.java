package com.railcab.audit.domain;


import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Port to the audit store. Implemented by a MongoDB adapter (default) and a PostgreSQL/JPA adapter, chosen
 * by {@code railcab.audit.persistence.type}; both must behave identically (see the shared contract test).
 */
public interface AuditEventRepository {

    /**
     * Inserts the events that are not stored yet. An event whose {@code eventId} already exists is skipped,
     * which makes producer retries safe. Ids are assigned here.
     *
     * @param events events with distinct eventIds
     * @return the eventIds that were actually inserted (the others already existed)
     */
    List<String> insertNew(List<AuditEvent> events);

    /** Stores payloads (keyed by eventId); one that already exists is left as it is. */
    void insertPayloads(List<AuditPayload> payloads);

    /** @return the payload of the event with this eventId */
    Optional<AuditPayload> findPayload(String eventId);

    /** @return payloads by eventId for the ids that have one */
    Map<String, AuditPayload> findPayloads(Collection<String> eventIds);

    /** @return the event with this database id */
    Optional<AuditEvent> findById(long id);

    /** Newest first (occurredAt desc, id desc). */
    PageResult<AuditEvent> search(AuditEventFilter filter, int page, int size);

    /** Newest first, at most {@code max} rows, for CSV export. */
    List<AuditEvent> searchForExport(AuditEventFilter filter, int max);

    /** Every event of one request chain within {@code scope}, oldest first, at most {@code max}. */
    List<AuditEvent> findByCorrelationId(String correlationId, int max, TenantScope scope);

    /** Distinct filter values within {@code scope} seen since {@code since}, each list capped at {@code maxValues}. */
    AuditFacets facets(Instant since, int maxValues, TenantScope scope);

    /**
     * Deletes events and payloads whose {@code expireAt} has passed.
     *
     * @param now the reference time
     * @return events removed
     */
    long deleteExpired(Instant now);
}
