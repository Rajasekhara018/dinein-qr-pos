package com.railcab.audit.application;

import static com.railcab.audit.application.IngestConstants.*;
import com.railcab.audit.domain.AuditException;
import com.railcab.audit.config.AuditProperties;
import com.railcab.audit.domain.AuditEvent;
import com.railcab.audit.domain.AuditEventType;
import com.railcab.audit.domain.AuditOutcome;
import com.railcab.audit.domain.AuditPayload;
import com.railcab.audit.domain.AuditEventRepository;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Accepts events from trusted services (internal endpoint) and from signed-in users' screens (screen-view
 * endpoint). Each event is sanitized on its own, so one bad event never fails the batch; valid ones are stamped
 * with their retention expiry and stored idempotently.
 */
@Service
public class AuditIngestService {

    private final AuditProperties properties;
    private final EventSanitizer sanitizer;
    private final RetentionPolicy retention;
    private final AuditEventRepository repository;
    private final SlidingWindowRateLimiter limiter;
    private final PayloadSanitizer payloadSanitizer;
    private final Clock clock;

    /**
     * Creates the service.
     *
     * @param properties service settings
     * @param sanitizer  event cleaner
     * @param retention  retention classes
     * @param repository the audit store
     * @param limiter    per-user screen-view limiter
     * @param payloadSanitizer header and body cleaner
     * @param clock      time source
     */
    public AuditIngestService(AuditProperties properties, EventSanitizer sanitizer, RetentionPolicy retention,
                              AuditEventRepository repository, SlidingWindowRateLimiter limiter, PayloadSanitizer payloadSanitizer,
                              Clock clock) {
        this.properties = properties;
        this.sanitizer = sanitizer;
        this.retention = retention;
        this.repository = repository;
        this.limiter = limiter;
        this.payloadSanitizer = payloadSanitizer;
        this.clock = clock;
    }

    /**
     * Stores a batch from a trusted service.
     *
     * @param events 1 to {@code max-batch-size} events
     * @return counts and the reasons for rejected events
     */
    public IngestResult ingestInternal(List<AuditEventInput> events) {
        int max = properties.getIngest().getMaxBatchSize();
        if (events == null || events.isEmpty()) {
            throw new AuditException(EMPTY_BATCH_MESSAGE);
        }
        if (events.size() > max) {
            throw new AuditException("A batch may hold at most " + max + " events", AuditException.Kind.TOO_LARGE);
        }
        return store(events, 0);
    }

    /**
     * Stores screen views for the signed-in user. Identity comes from the caller, never from the body.
     *
     * @param user   the authenticated caller
     * @param views  1 to {@code screen-view-max-batch-size} views
     * @param client request metadata taken from the HTTP request
     * @return counts; {@code dropped} is positive when the per-user limit cut the batch
     */
    public IngestResult ingestScreenViews(CallerIdentity user, List<ScreenViewInput> views, ClientInfo client) {
        int max = properties.getIngest().getScreenViewMaxBatchSize();
        if (views == null || views.isEmpty()) {
            throw new AuditException(EMPTY_BATCH_MESSAGE);
        }
        if (views.size() > max) {
            throw new AuditException("A batch may hold at most " + max + " screen views", AuditException.Kind.TOO_LARGE);
        }
        int granted = limiter.acquire(user.userId(), views.size(), properties.getIngest().getScreenViewsPerMinute());
        List<AuditEventInput> inputs = new ArrayList<>();
        for (ScreenViewInput v : views.subList(0, granted)) {
            inputs.add(toInput(user, v, client));
        }
        IngestResult stored = inputs.isEmpty() ? new IngestResult(0, 0, 0, 0, 0, List.of()) : store(inputs, 0);
        return new IngestResult(views.size(), stored.accepted(), stored.duplicates(), stored.rejected(),
                views.size() - granted, stored.rejections());
    }

    private AuditEventInput toInput(CallerIdentity user, ScreenViewInput v, ClientInfo client) {
        String channel = v == null || v.appChannel() == null ? null : v.appChannel().toUpperCase(Locale.ROOT);
        Map<String, Object> details = new LinkedHashMap<>();
        if (v != null && v.details() != null) {
            v.details().forEach((k, val) -> {
                if (SCREEN_DETAIL_KEYS.contains(k)) {
                    details.put(k, val);
                }
            });
        }
        if (channel != null && !CLIENT_CHANNELS.contains(channel)) {
            channel = INVALID_CHANNEL;
        }
        return new AuditEventInput(v == null ? null : v.eventId(), AuditEventType.SCREEN_VIEW.name(), v == null ? null : v.occurredAt(),
                CLIENT_SOURCE, user.userId(), null, user.role(), user.roles(), List.of(),
                v == null ? null : v.feature(), null, v == null ? null : v.screenKey(), null, null, null, AuditOutcome.SUCCESS.name(),
                v == null ? null : v.durationMs(), client.correlationId(), client.requestId(), client.ipAddress(),
                client.userAgent(), channel, user.tenantId(), null, null, null, details, null, null, null, null, null, null, null, null, null);
    }

    private IngestResult store(List<AuditEventInput> inputs, int dropped) {
        Instant now = clock.instant();
        List<AuditEvent> valid = new ArrayList<>();
        Map<String, AuditPayload> payloads = new HashMap<>();
        List<IngestResult.Rejection> rejections = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        int duplicatesInBatch = 0;
        for (int i = 0; i < inputs.size(); i++) {
            EventSanitizer.Outcome outcome = sanitizer.sanitize(inputs.get(i), now);
            if (outcome.event() == null) {
                rejections.add(new IngestResult.Rejection(i, outcome.error()));
                continue;
            }
            AuditEvent event = outcome.event();
            if (!seen.add(event.getEventId())) {
                duplicatesInBatch++;
                continue;
            }
            event.setExpireAt(retention.expireAt(event, now));
            AuditPayload payload = payloadSanitizer.build(inputs.get(i), event);
            if (payload != null) {
                payloads.put(event.getEventId(), payload);
            }
            valid.add(event);
        }
        List<String> insertedIds = valid.isEmpty() ? List.of() : repository.insertNew(valid);
        int inserted = insertedIds.size();
        List<AuditPayload> newPayloads = insertedIds.stream().map(payloads::get).filter(java.util.Objects::nonNull).toList();
        if (!newPayloads.isEmpty()) {
            repository.insertPayloads(newPayloads);
        }
        return new IngestResult(inputs.size(), inserted, duplicatesInBatch + (valid.size() - inserted),
                rejections.size(), dropped, rejections);
    }
}
