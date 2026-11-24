package com.heuristiq.uam.search;

/** Fixed values for the search re-index. */
final class SearchReindexConstants {

    /** Entities read per page when re-indexing; bounds memory and the burst of events put on the stream. */
    static final int MAX_BATCH_SIZE = 1000;

    /** Used when {@code railcab.search.reindex.batch-size} is not set. */
    static final int DEFAULT_BATCH_SIZE = 200;

    /** Stable sort key for paging, so no entity is skipped or repeated when pages are read one after another. */
    static final String SORT_FIELD = "id";

    /** Search index stream record fields and operations (read by Rider_SearchSync). */
    static final String FIELD_OPERATION = "operation";
    static final String FIELD_MODEL = "model";
    static final String FIELD_MODEL_KEY = "modelKey";
    static final String FIELD_PAYLOAD = "payload";
    static final String OPERATION_INDEX = "INDEX";
    static final String OPERATION_DELETE = "DELETE";

    private SearchReindexConstants() {
    }
}
