package com.heuristiq.gateway.audit;

import static com.heuristiq.gateway.audit.AuditConstants.*;
import org.springframework.stereotype.Component;

/**
 * Applies {@link AuditPayloadRedactor} to an event's headers and bodies. Runs on the batcher thread right before
 * an event is sent, so nothing unredacted is ever posted or persisted. Bodies of content types that are not JSON,
 * text or form posts, and bodies of sensitive paths, are dropped here as well (defence in depth: the capture
 * already avoids collecting them).
 */
@Component
public class AuditEventRedactor {

    private final AuditProperties properties;
    private final AuditPathRules rules;

    /**
     * Creates the redactor.
     *
     * @param properties audit settings
     * @param rules      path rules
     */
    public AuditEventRedactor(AuditProperties properties, AuditPathRules rules) {
        this.properties = properties;
        this.rules = rules;
    }

    /**
     * @param event the event as captured
     * @return the same event with credentials removed from headers and bodies
     */
    public GatewayAuditEvent redact(GatewayAuditEvent event) {
        event.setRequestHeaders(AuditPayloadRedactor.redactHeaders(event.getRequestHeaders(), MAX_HEADERS));
        event.setResponseHeaders(AuditPayloadRedactor.redactHeaders(event.getResponseHeaders(), MAX_HEADERS));
        boolean keepBodies = !rules.bodySkipped(event.getPath()) && !rules.captureExcluded(event.getPath());
        event.setRequestBody(body(keepBodies, event.getRequestBody(), event.getRequestContentType()));
        event.setResponseBody(body(keepBodies, event.getResponseBody(), event.getResponseContentType()));
        return event;
    }

    private String body(boolean keep, String body, String contentType) {
        if (!keep || body == null || !AuditPayloadRedactor.isCapturableContentType(contentType)) {
            return null;
        }
        String redacted = AuditPayloadRedactor.redactBody(body, contentType);
        int max = properties.getMaxBodyBytes();
        return redacted != null && redacted.length() > max ? redacted.substring(0, max) : redacted;
    }
}
