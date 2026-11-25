package com.heuristiq.documents.search.projection;

import java.time.Duration;

/**
 * Fixed values of the web-model projections DMS publishes to Rider_SearchAPI: model names, the ingress wire contract
 * (path, body keys, slot names) and the resilience settings of that call. Per-environment values (address, token,
 * timeout) are in {@code SearchProjectionProperties}, not here.
 */
public final class ProjectionConstants {

    private ProjectionConstants() {
    }

    // ---- models (names in Rider_SearchAPI's web-search-models.json)
    public static final String MODEL_DOCUMENT_TYPE = "WEB_DMS_DOCUMENT_TYPE";
    public static final String MODEL_LIFECYCLE_DOCUMENT = "WEB_DMS_LIFECYCLE_DOCUMENT";

    // ---- ingress wire contract
    public static final String INGRESS_PATH = "/api/v1/search/internal/projections";
    public static final String BODY_PHASE = "phase";
    public static final String BODY_MODEL = "model";
    public static final String BODY_TENANT_ID = "tenantId";
    public static final String BODY_PARTITION = "partition";
    public static final String BODY_GENERATION = "generation";
    public static final String BODY_RECORDS = "records";
    public static final String BODY_MODEL_KEY = "modelKey";
    public static final String BODY_FIELDS = "fields";

    /** DMS publishes each model of an organization as one partition. */
    public static final String PARTITION = "dms";
    public static final String GENERATION_PREFIX = "dms-";
    /** The ingress accepts at most this many records per call; the source is read in pages of the same size. */
    public static final int BATCH_SIZE = 100;

    // ---- record fields (slots as the ingress and the assembler read them)
    public static final String FIELD_PAYLOAD = "payload";
    public static final String FIELD_CREATED_AT = "createdAt";
    public static final String SLOT_TEXT1 = "text1";
    public static final String SLOT_TEXT2 = "text2";
    public static final String SLOT_TEXT3 = "text3";
    public static final String SLOT_TEXT4 = "text4";
    public static final String SLOT_TEXT5 = "text5";
    public static final String SLOT_TEXT6 = "text6";
    /** The free-text search slot of the lifecycle model (lower-cased; the web client lower-cases the typed query). */
    public static final String SLOT_QUERY_TEXT = "text13";
    public static final String SLOT_DATETIME1 = "datetime1";
    /** The row's position in the source's own order (the catalogue sorts {@code sourceOrdered} models by it). */
    public static final String SLOT_POSITION = "number5";
    public static final String QUERY_TEXT_SEPARATOR = " ";

    // ---- HTTP
    public static final int HTTP_CONFLICT = 409;
    public static final int HTTP_CLIENT_ERROR_FLOOR = 400;
    public static final int HTTP_SERVER_ERROR_FLOOR = 500;

    // ---- messages
    public static final String MESSAGE_IN_PROGRESS =
            "another projection run is in progress for this organization; try again in a few minutes";
    public static final String MESSAGE_NOT_CONFIGURED = "the search projection is not configured (service token missing)";

    // ---- resilient client (wiring)
    public static final String DOWNSTREAM = "search-api";
    public static final String WEB_CLIENT_BEAN = "searchProjectionWebClient";
    public static final String RESILIENT_CALLER_BEAN = "searchProjectionResilientCaller";
    public static final int MAX_CONCURRENT_CALLS = 2;
    public static final int BREAKER_WINDOW = 10;
    public static final int BREAKER_MIN_CALLS = 5;
    public static final float BREAKER_FAILURE_PERCENT = 50f;
    public static final Duration BREAKER_OPEN = Duration.ofSeconds(20);
    public static final long MIN_TIMEOUT_MS = 500;
}
