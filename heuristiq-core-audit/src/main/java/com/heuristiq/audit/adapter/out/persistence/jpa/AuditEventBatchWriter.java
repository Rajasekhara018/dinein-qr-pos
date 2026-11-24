package com.heuristiq.audit.adapter.out.persistence.jpa;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.heuristiq.audit.domain.AuditEvent;
import com.heuristiq.audit.domain.AuditPayload;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Writes audit events and their payloads with multi-row {@code INSERT ... ON CONFLICT (event_id) DO NOTHING}
 * statements: one round trip per {@link AuditInsertSql#ROWS_PER_STATEMENT} rows, and an event or payload that is
 * already stored (a producer's retry, or another instance storing it at the same moment) is skipped by the database
 * instead of being looked up first. Writes exactly the columns and values {@link AuditEventEntity#from} and
 * {@link AuditPayloadEntity#from} map, so stored rows are the same as with the JPA save it replaces.
 */
@Component
@ConditionalOnProperty(prefix = "railcab.audit.persistence", name = "type", havingValue = "postgres")
public class AuditEventBatchWriter {

    /** Serialises the string maps stored in jsonb columns; a local detail of this writer. */
    private static final ObjectMapper JSON = new ObjectMapper();

    private final JdbcTemplate jdbc;

    /**
     * @param jdbc the template over the audit database
     */
    public AuditEventBatchWriter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Inserts the events whose event_id is not stored yet.
     *
     * @param events events with distinct event ids
     * @return the event ids actually inserted, in the order of {@code events}
     */
    public List<String> insertEvents(List<AuditEvent> events) {
        Set<String> inserted = new HashSet<>();
        for (List<AuditEvent> chunk : chunks(events)) {
            String sql = AuditInsertSql.insertSkippingDuplicates(AuditInsertSql.EVENTS_TABLE, AuditInsertSql.EVENT_COLUMNS,
                    chunk.size()) + AuditInsertSql.RETURNING_EVENT_ID;
            inserted.addAll(jdbc.query(sql, ps -> bindEvents(ps, chunk),
                    (rs, rowNum) -> rs.getString(AuditInsertSql.EVENT_ID_COLUMN)));
        }
        return events.stream().map(AuditEvent::getEventId).filter(inserted::contains).toList();
    }

    /**
     * Inserts the payloads whose event_id is not stored yet; a stored payload is left as it is.
     *
     * @param payloads payloads with distinct event ids
     */
    public void insertPayloads(List<AuditPayload> payloads) {
        for (List<AuditPayload> chunk : chunks(payloads)) {
            String sql = AuditInsertSql.insertSkippingDuplicates(AuditInsertSql.PAYLOADS_TABLE,
                    AuditInsertSql.PAYLOAD_COLUMNS, chunk.size());
            jdbc.update(sql, ps -> bindPayloads(ps, chunk));
        }
    }

    private static void bindEvents(PreparedStatement ps, List<AuditEvent> chunk) throws SQLException {
        int i = 1;
        for (AuditEvent e : chunk) {
            ps.setObject(i++, e.getEventId());
            ps.setObject(i++, name(e.getEventType()));
            ps.setObject(i++, utc(e.getOccurredAt()));
            ps.setObject(i++, utc(e.getReceivedAt()));
            ps.setObject(i++, utc(e.getExpireAt()));
            ps.setObject(i++, e.getSourceService());
            ps.setObject(i++, e.getUserId());
            ps.setObject(i++, e.getUserType());
            ps.setObject(i++, e.getRole());
            ps.setObject(i++, AuditEventEntity.join(e.getRoles()));
            ps.setObject(i++, AuditEventEntity.join(e.getPrivilegeIds()));
            ps.setObject(i++, e.getFeature());
            ps.setObject(i++, e.getModule());
            ps.setObject(i++, e.getScreenKey());
            ps.setObject(i++, e.getMethod());
            ps.setObject(i++, e.getPath());
            ps.setObject(i++, e.getStatusCode());
            ps.setObject(i++, name(e.getOutcome()));
            ps.setObject(i++, e.getDurationMs());
            ps.setObject(i++, e.getCorrelationId());
            ps.setObject(i++, e.getRequestId());
            ps.setObject(i++, e.getIpAddress());
            ps.setObject(i++, e.getUserAgent());
            ps.setObject(i++, name(e.getAppChannel()));
            ps.setObject(i++, e.getTenantId());
            ps.setObject(i++, e.getSubjectType());
            ps.setObject(i++, e.getSubjectId());
            ps.setObject(i++, e.getReasonCode());
            ps.setObject(i++, json(e.getDetails()));
        }
    }

    private static void bindPayloads(PreparedStatement ps, List<AuditPayload> chunk) throws SQLException {
        int i = 1;
        for (AuditPayload p : chunk) {
            ps.setObject(i++, p.getEventId());
            ps.setObject(i++, json(p.getRequestHeaders()));
            ps.setObject(i++, json(p.getResponseHeaders()));
            ps.setObject(i++, p.getRequestBody());
            ps.setObject(i++, p.getResponseBody());
            ps.setBoolean(i++, p.isRequestBodyTruncated());
            ps.setBoolean(i++, p.isResponseBodyTruncated());
            ps.setObject(i++, p.getRequestBodySize());
            ps.setObject(i++, p.getResponseBodySize());
            ps.setObject(i++, p.getBodyContentType());
            ps.setObject(i++, utc(p.getExpireAt()));
        }
    }

    private static <T> List<List<T>> chunks(List<T> items) {
        List<List<T>> out = new ArrayList<>();
        for (int from = 0; from < items.size(); from += AuditInsertSql.ROWS_PER_STATEMENT) {
            out.add(items.subList(from, Math.min(items.size(), from + AuditInsertSql.ROWS_PER_STATEMENT)));
        }
        return out;
    }

    private static String name(Enum<?> value) {
        return value == null ? null : value.name();
    }

    private static OffsetDateTime utc(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }

    private static String json(Map<String, String> map) {
        if (map == null) {
            return null;
        }
        try {
            return JSON.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            // A map of strings always serialises; this cannot happen.
            throw new IllegalStateException("Could not serialise an audit map", e);
        }
    }
}
