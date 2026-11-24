package com.heuristiq.search.common;

/** Fixed messages and machine-readable error codes of the service's standard response envelope. */
public final class CommonConstants {

    public static final String SUCCESS_MESSAGE = "Success";
    public static final String VALIDATION_FAILED_MESSAGE = "Validation failed";
    public static final String UNEXPECTED_ERROR_MESSAGE = "An unexpected error occurred";

    public static final String ERROR_VALIDATION = "VALIDATION_ERROR";
    public static final String ERROR_NOT_FOUND = "NOT_FOUND";
    public static final String ERROR_TENANT_REQUIRED = "TENANT_REQUIRED";
    public static final String ERROR_SEARCH_INDEX_UNAVAILABLE = "SEARCH_INDEX_UNAVAILABLE";

    private CommonConstants() {
    }
}
