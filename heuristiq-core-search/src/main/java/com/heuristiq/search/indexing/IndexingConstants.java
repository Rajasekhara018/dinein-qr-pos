package com.heuristiq.search.indexing;

/** Fixed values of the Redis Stream index-event contract shared with the producing services. */
public final class IndexingConstants {

    /** Field names of one stream entry. */
    public static final String FIELD_OPERATION = "operation";
    public static final String FIELD_MODEL = "model";
    public static final String FIELD_MODEL_KEY = "modelKey";
    public static final String FIELD_PAYLOAD = "payload";

    /** Values of {@link #FIELD_OPERATION}. */
    public static final String OPERATION_INDEX = "INDEX";
    public static final String OPERATION_DELETE = "DELETE";

    /** Redis error text when the consumer group already exists. */
    public static final String BUSYGROUP_ERROR = "BUSYGROUP";

    /**
     * Pause after an error reading the stream (Redis down or a dropped connection) before the next read, so an outage logs one
     * line a second instead of spinning, and the subscription keeps going instead of ending.
     */
    public static final java.time.Duration STREAM_READ_ERROR_PAUSE = java.time.Duration.ofSeconds(1);

    private IndexingConstants() {
    }
}
