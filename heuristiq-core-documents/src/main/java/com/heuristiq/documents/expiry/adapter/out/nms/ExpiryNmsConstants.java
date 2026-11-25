package com.heuristiq.documents.expiry.adapter.out.nms;

import java.time.Duration;
import java.util.List;

/** Wire values of the expiry reminders sent to NMS (D12). */
public final class ExpiryNmsConstants {

    public static final String NMS_RESILIENT_CALLER_BEAN = "expiryNmsResilientCaller";
    public static final String NMS_WEB_CLIENT_BEAN = "expiryNmsWebClient";
    public static final String NMS_DOWNSTREAM = "nms";
    public static final String NOTIFY_PATH = "/api/v1/nms/notify";
    public static final String API_KEY_HEADER = "X-NMS-API-Key";
    /** A base URL naming localhost, an IP or a port is a direct address and skips the load balancer. */
    public static final String DIRECT_ADDRESS_REGEX = "^https?://(localhost|[0-9.]+|[^/]+:[0-9]+)(/.*)?$";

    public static final int MAX_CONCURRENT_CALLS = 5;
    public static final int BREAKER_WINDOW = 20;
    public static final int BREAKER_MIN_CALLS = 10;
    public static final int BREAKER_FAILURE_PERCENT = 50;
    public static final Duration BREAKER_OPEN = Duration.ofSeconds(30);

    /** Origin of every reminder, so NMS can trace it from origin to destination. */
    public static final String SOURCE_SERVICE = "dms";
    public static final String EVENT_EXPIRING = "DRIVER_DOCUMENT_EXPIRING";
    public static final String EVENT_LAPSED = "DRIVER_DOCUMENT_LAPSED";
    public static final String ENTITY_TYPE = "DOCUMENT";
    /** SMS goes to the number UAM holds for the driver (NMS looks it up by user id). */
    public static final List<String> CHANNELS = List.of("IN_APP", "FCM_PUSH", "SMS");
    public static final String CORRELATION_MDC_KEY = "correlationId";
    /** Message id (NMS's idempotency key): one per event, document, expiry date and reminder. */
    public static final String MESSAGE_ID_FORMAT = "dms:%s:%s:%s:%d";

    /** Template variables. */
    public static final String VAR_CATEGORY = "category";
    public static final String VAR_VALID_UNTIL = "validUntil";
    public static final String VAR_DAYS_LEFT = "daysLeft";
    public static final String VAR_DOCUMENT_NAME = "documentName";

    /** The document's name in the message (the templates are English), by tracked category; the category itself when unknown. */
    public static final java.util.Map<String, String> DOCUMENT_NAMES = java.util.Map.of(
            "driver-license", "driving licence", "driver-rc", "vehicle RC", "driver-insurance", "vehicle insurance",
            "driver-puc", "pollution certificate (PUC)", "driver-permit", "permit", "driver-fitness", "fitness certificate");

    private ExpiryNmsConstants() {
    }
}
