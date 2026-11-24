package com.railcab.nms.adapters.out.integration.audit;

/** Wire constants of the audit service ingest API. */
final class AuditHttpConstants {

    static final String INGEST_PATH = "/api/v1/audit/internal/events";
    static final String HEADER_SERVICE_TOKEN = "X-Rider-Service-Token";
    static final String EVENTS_PREFIX = "{\"events\":[";
    static final String EVENTS_SUFFIX = "]}";

    private AuditHttpConstants() {
    }
}
