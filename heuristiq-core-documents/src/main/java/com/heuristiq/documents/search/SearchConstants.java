package com.railcab.dms.search;

/** Fixed values of search indexing: the stream record fields and operations, the model name and re-index batch sizes. */
public final class SearchConstants {

    private SearchConstants() {
    }

    /** 2026_RailCab_SearchAPI's SearchModel value for a document. */
    public static final String MODEL_DOCUMENT = "DOCUMENT";

    /** Answer when the session carries no organization (the web models are published per organization). */
    public static final String NO_ORGANIZATION_MESSAGE = "Your session has no organization";

    // ---- stream record (contract with 2026_RailCab_SearchAPI)
    public static final String OPERATION_INDEX = "INDEX";
    public static final String OPERATION_DELETE = "DELETE";
    public static final String FIELD_OPERATION = "operation";
    public static final String FIELD_MODEL = "model";
    public static final String FIELD_MODEL_KEY = "modelKey";
    public static final String FIELD_PAYLOAD = "payload";

    // ---- re-index
    /** Default page size of a re-index run (also the {@code railcab.search.reindex.batch-size} default). */
    public static final int DEFAULT_BATCH_SIZE = 200;
    public static final int MAX_BATCH_SIZE = 1000;
    /** Name of the virtual thread that runs a re-index. */
    public static final String REINDEX_THREAD_NAME = "search-reindex";
}
