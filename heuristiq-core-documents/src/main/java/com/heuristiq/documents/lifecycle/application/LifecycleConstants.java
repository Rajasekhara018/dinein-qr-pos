package com.railcab.dms.lifecycle.application;

/** Fixed values of the document lifecycle (troubleshooting) queries. */
public final class LifecycleConstants {

    private LifecycleConstants() {
    }

    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;
    /** Longest free-text search accepted; longer input is refused. */
    public static final int MAX_QUERY_LENGTH = 100;
    /** Longest value accepted for an exact-match filter. */
    public static final int MAX_FILTER_LENGTH = 128;
    public static final String NO_ORGANIZATION_MESSAGE = "Your session has no organization";
    public static final String DOCUMENT_NOT_FOUND_MESSAGE = "Document not found";
}
