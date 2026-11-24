package com.railcab.nms.search;

/** Fixed values of the search-index integration: stream record field names, reindex batch limits and thread name. */
final class SearchConstants {

    // fields of a record on the search index stream (contract with the Search API consumer)
    static final String FIELD_OPERATION = "operation";
    static final String FIELD_MODEL = "model";
    static final String FIELD_MODEL_KEY = "modelKey";
    static final String FIELD_PAYLOAD = "payload";

    /** Default and largest reindex batch; the configured value ({@code railcab.search.reindex.batch-size}) is clamped. */
    static final int DEFAULT_REINDEX_BATCH_SIZE = 200;
    static final int MAX_REINDEX_BATCH_SIZE = 1000;

    /** Name of the virtual thread that runs a reindex. */
    static final String REINDEX_THREAD_NAME = "search-reindex";

    private SearchConstants() {
    }
}
