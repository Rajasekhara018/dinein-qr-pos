package com.heuristiq.paymentservice.gatewayaudit.publish;

import java.util.List;

/** Port: delivers a batch of serialised audit events to the audit service. */
public interface AuditEventSink {

    /**
     * Sends the events (each a complete JSON object) in one request. Ingest is idempotent on
     * {@code eventId}, so resending is safe.
     *
     * @throws RuntimeException when the audit service is unavailable or refuses the whole request
     */
    IngestResult send(List<String> eventJson);

    /** Counts reported by the audit service; zero when the reply could not be read. */
    record IngestResult(int received, int accepted, int duplicates, int rejected) {
    }
}
