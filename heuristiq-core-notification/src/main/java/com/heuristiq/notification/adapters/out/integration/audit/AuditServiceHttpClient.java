package com.heuristiq.notification.adapters.out.integration.audit;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import com.heuristiq.notification.application.port.out.AuditEventSink;
import com.heuristiq.notification.infrastructure.config.NmsProperties;
import com.heuristiq.resilient.Idempotency;
import com.heuristiq.resilient.ResilientCaller;

/**
 * Posts batches of COMMUNICATION events to the central audit service
 * ({@code POST /api/v1/audit/internal/events}, service token). Goes through the shared
 * {@link ResilientCaller} (timeout, bulkhead, circuit breaker). The call is marked
 * {@link Idempotency#SAFE} because the audit service stores an event once per {@code eventId}, so a
 * retried or re-sent batch cannot duplicate anything.
 *
 * <p>The audit service being down surfaces as an {@link AuditEventSink.AuditSinkException}; the
 * caller (the outbox drain) keeps the events and tries again later.
 */
@Component
public class AuditServiceHttpClient implements AuditEventSink {

    private static final Logger logger = LoggerFactory.getLogger(AuditServiceHttpClient.class);

    private final NmsProperties props;
    private final WebClient webClient;
    private final ResilientCaller auditResilientCaller;

    public AuditServiceHttpClient(NmsProperties props, WebClient webClient, ResilientCaller auditResilientCaller) {
        this.props = props;
        this.webClient = webClient;
        this.auditResilientCaller = auditResilientCaller;
    }

    @Override
    public void send(List<String> eventsJson) {
        if (eventsJson == null || eventsJson.isEmpty()) {
            return;
        }
        NmsProperties.Audit.Publish cfg = props.getAudit().getPublish();
        String body = AuditHttpConstants.EVENTS_PREFIX + String.join(",", eventsJson) + AuditHttpConstants.EVENTS_SUFFIX;
        String url = trimTrailingSlash(cfg.getUrl()) + AuditHttpConstants.INGEST_PATH;
        try {
            auditResilientCaller.callBlocking(() -> webClient.post()
                    .uri(url)
                    .header(AuditHttpConstants.HEADER_SERVICE_TOKEN, cfg.getServiceToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .toBodilessEntity(), Idempotency.SAFE);
            logger.debug("Sent {} communication audit events", eventsJson.size());
        } catch (RuntimeException e) {
            throw new AuditSinkException("Audit service did not accept the batch: " + e.getMessage(), e);
        }
    }

    private static String trimTrailingSlash(String url) {
        return url != null && url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
