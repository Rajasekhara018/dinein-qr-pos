package com.heuristiq.search.api;

/** Fixed HTTP paths of the search API's controllers. */
public final class SearchApiConstants {

    /** Base path of every search endpoint, as served behind the gateway. */
    public static final String BASE_PATH = "/api/v1/search";

    /** Elastic-style list endpoints. */
    public static final String ELASTIC_PATH = BASE_PATH + "/elastic";

    /** Organization-scoping diagnostics. */
    public static final String SCOPE_PATH = BASE_PATH + "/scope";

    private SearchApiConstants() {
    }
}
