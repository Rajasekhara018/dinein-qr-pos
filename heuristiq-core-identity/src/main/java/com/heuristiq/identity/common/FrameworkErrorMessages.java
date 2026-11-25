package com.heuristiq.identity.common;

/**
 * Plain-language messages for errors that Spring raises itself before or around a controller (no such path, wrong HTTP method,
 * unreadable body). They are fixed text so the response never carries a class name, a path from the code or a stack trace.
 */
final class FrameworkErrorMessages {

    static final String NOT_FOUND = "Not found.";
    static final String METHOD_NOT_ALLOWED = "This request method is not supported here.";
    static final String UNSUPPORTED_MEDIA_TYPE = "This content type is not supported.";
    static final String NOT_ACCEPTABLE = "This response format is not available.";
    static final String SERVICE_UNAVAILABLE = "This service is temporarily unavailable. Please try again in a moment.";
    static final String BAD_REQUEST = "The request could not be understood. Check the parameters and the body.";

    private FrameworkErrorMessages() {
    }
}
