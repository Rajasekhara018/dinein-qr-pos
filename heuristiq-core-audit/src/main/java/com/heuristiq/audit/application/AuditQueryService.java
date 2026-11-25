package com.heuristiq.audit.application;

import com.heuristiq.audit.domain.AuditException;
import com.heuristiq.audit.domain.PageResult;
import com.heuristiq.audit.config.AuditProperties;
import com.heuristiq.audit.domain.AppChannel;
import com.heuristiq.audit.domain.AuditEvent;
import com.heuristiq.audit.domain.AuditEventFilter;
import com.heuristiq.audit.domain.AuditEventType;
import com.heuristiq.audit.domain.AuditFacets;
import com.heuristiq.audit.domain.AuditOutcome;
import com.heuristiq.audit.domain.AuditEventRepository;
import com.heuristiq.audit.domain.TenantScope;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;

/** Read side of the audit store: validates filters, caps sizes and shapes the results. */
@Service
public class AuditQueryService {


    private final AuditEventRepository repository;
    private final AuditProperties.Query settings;
    private final Clock clock;

    /**
     * Creates the service.
     *
     * @param repository the audit store
     * @param properties service settings
     * @param clock      time source
     */
    public AuditQueryService(AuditEventRepository repository, AuditProperties properties, Clock clock) {
        this.repository = repository;
        this.settings = properties.getQuery();
        this.clock = clock;
    }

    /**
     * @param caller the reader; decides which organizations' records are visible
     * @param q    filters
     * @param page zero-based page
     * @param size page size, capped at the configured maximum
     * @return the page, newest first
     */
    public PageResult<AuditEventView> search(CallerIdentity caller, EventQuery q, int page, int size) {
        if (page < 0) {
            throw new AuditException("page must not be negative");
        }
        int safeSize = Math.min(Math.max(size, 1), settings.getMaxPageSize());
        PageResult<AuditEvent> found = repository.search(toFilter(q, scopeOf(caller)), page, safeSize);
        return new PageResult<>(found.content().stream().map(AuditEventView::from).toList(), found.totalElements(),
                found.totalPages(), found.size(), found.number(), found.numberOfElements(), found.first(), found.last(),
                found.empty());
    }

    /**
     * @param caller the reader
     * @param id database id
     * @return the event
     * @throws AuditException 404 when absent or owned by an organization the caller can not reach
     */
    public AuditEventView get(CallerIdentity caller, long id) {
        return visible(caller, id).map(AuditEventView::from)
                .orElseThrow(() -> AuditException.notFound(QueryConstants.EVENT_NOT_FOUND));
    }

    /**
     * @param caller        the reader
     * @param correlationId the request chain id
     * @return its events the caller may see, oldest first (at most 500)
     */
    public List<AuditEventView> byCorrelation(CallerIdentity caller, String correlationId) {
        if (correlationId == null || correlationId.isBlank()) {
            throw new AuditException("correlationId is required");
        }
        return repository.findByCorrelationId(correlationId, QueryConstants.CORRELATION_MAX, scopeOf(caller)).stream().map(AuditEventView::from).toList();
    }

    /**
     * @param caller the reader
     * @param q filters
     * @return the newest matching events, capped at {@code export-max-rows}
     */
    public List<AuditEventView> exportEvents(CallerIdentity caller, EventQuery q) {
        return repository.searchForExport(toFilter(q, scopeOf(caller)), settings.getExportMaxRows()).stream()
                .map(AuditEventView::from).toList();
    }

    /**
     * Events together with their stored payloads, for the payload export.
     *
     * @param events   the events
     * @param payloads payloads by eventId (events without one are absent)
     */
    public record PayloadExport(List<AuditEventView> events, Map<String, AuditPayloadView> payloads) {
    }

    /**
     * @param caller the reader
     * @param id database id of the event
     * @return its redacted headers and bodies
     * @throws AuditException 404 when the event (or one of another organization) or its payload does not exist
     */
    public AuditPayloadView payload(CallerIdentity caller, long id) {
        AuditEvent event = visible(caller, id).orElseThrow(() -> AuditException.notFound(QueryConstants.EVENT_NOT_FOUND));
        return repository.findPayload(event.getEventId()).map(AuditPayloadView::from)
                .orElseThrow(() -> AuditException.notFound(QueryConstants.PAYLOAD_NOT_FOUND));
    }

    /**
     * @param caller the reader
     * @param q filters
     * @return the newest matching events with their stored headers and bodies, capped at
     *         {@code export-payload-max-rows}
     */
    public PayloadExport exportPayloads(CallerIdentity caller, EventQuery q) {
        List<AuditEvent> events = repository.searchForExport(toFilter(q, scopeOf(caller)), settings.getExportPayloadMaxRows());
        Map<String, AuditPayloadView> payloads = new HashMap<>();
        repository.findPayloads(events.stream().map(AuditEvent::getEventId).toList())
                .forEach((id, p) -> payloads.put(id, AuditPayloadView.from(p)));
        return new PayloadExport(events.stream().map(AuditEventView::from).toList(), payloads);
    }

    /**
     * @param caller the reader
     * @return dropdown values from the recent window, from records the caller may see
     */
    public AuditFacets facets(CallerIdentity caller) {
        Instant since = clock.instant().minus(Duration.ofDays(settings.getFacetWindowDays()));
        return repository.facets(since, settings.getFacetMaxValues(), scopeOf(caller));
    }

    /**
     * The organizations a caller may read: everything for a cross-organization view, otherwise the session's
     * organization plus legacy records without one (a session with none sees only the legacy records).
     *
     * @param caller the reader
     * @return the scope
     */
    static TenantScope scopeOf(CallerIdentity caller) {
        if (caller == null) {
            return TenantScope.of(null);
        }
        return caller.allOrganizations() ? TenantScope.ALL : TenantScope.of(caller.tenantId());
    }

    /** An event that exists and is inside the caller's scope; another organization's event looks absent. */
    private java.util.Optional<AuditEvent> visible(CallerIdentity caller, long id) {
        TenantScope scope = scopeOf(caller);
        return repository.findById(id).filter(e -> scope.allows(e.getTenantId()));
    }

    AuditEventFilter toFilter(EventQuery q, TenantScope scope) {
        Instant from = instant("from", q.from());
        Instant to = instant("to", q.to());
        if (from != null && to != null && to.isBefore(from)) {
            throw new AuditException("to must not be before from");
        }
        return new AuditEventFilter(blank(q.userId()), blank(q.role()), blank(q.feature()), blank(q.screenKey()),
                enumOf(AuditEventType.class, "eventType", q.eventType()), enumOf(AuditOutcome.class, "outcome", q.outcome()),
                enumOf(AppChannel.class, "channel", q.channel()), q.privilegeId(), blank(q.method()), blank(q.path()),
                blank(q.correlationId()), from, to, code("subjectType", q.subjectType(), IngestConstants.MAX_SUBJECT_TYPE),
                subjectId(q.subjectId()), code("reasonCode", q.reasonCode(), IngestConstants.MAX_REASON_CODE), scope);
    }

    /** Exact-match UPPER_SNAKE filter value; anything that could not be a stored code is a 400, never a query. */
    private static String code(String name, String raw, int max) {
        String v = blank(raw);
        if (v == null) {
            return null;
        }
        v = v.toUpperCase(Locale.ROOT);
        if (v.length() > max || !IngestConstants.UPPER_SNAKE_CODE.matcher(v).matches()) {
            throw new AuditException(name + " must be UPPER_SNAKE (A-Z 0-9 _), max " + max + " characters");
        }
        return v;
    }

    private static String subjectId(String raw) {
        String v = blank(raw);
        if (v != null && (v.length() > IngestConstants.MAX_SUBJECT_ID || !IngestConstants.SUBJECT_ID.matcher(v).matches())) {
            throw new AuditException("subjectId must be 1-" + IngestConstants.MAX_SUBJECT_ID + " characters of A-Z a-z 0-9 _ . : -");
        }
        return v;
    }

    private static String blank(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }

    private static Instant instant(String name, String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(raw.trim());
        } catch (DateTimeParseException e) {
            throw new AuditException(name + " must be an ISO-8601 instant, e.g. 2026-10-06T00:00:00Z");
        }
    }

    private static <E extends Enum<E>> E enumOf(Class<E> type, String name, String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Enum.valueOf(type, raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new AuditException(name + " is not a valid value");
        }
    }
}
