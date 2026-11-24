package com.railcab.audit.common;

/** Fixed texts and codes of the shared {@link CommonResponseObject} envelope and the error handler. */
public final class CommonConstants {

    private CommonConstants() {
    }

    public static final String SUCCESS_MESSAGE = "Success";
    public static final String VALIDATION_FAILED_MESSAGE = "Validation failed";
    public static final String VALIDATION_ERROR_CODE = "VALIDATION_ERROR";
    public static final String MALFORMED_INPUT_MESSAGE = "Malformed or missing request input";
    public static final String METHOD_NOT_ALLOWED_MESSAGE = "Method not allowed";
    public static final String UNSUPPORTED_MEDIA_TYPE_MESSAGE = "Unsupported media type";
    public static final String NOT_FOUND_MESSAGE = "Not found";
    public static final String UNEXPECTED_ERROR_MESSAGE = "An unexpected error occurred. Please try again later.";
}
