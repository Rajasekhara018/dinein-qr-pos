package com.railcab.nms.application.port.out;

/**
 * The audience for a broadcast could not be resolved completely (identity source unavailable, an
 * error page, or more pages than the sweep limit). Thrown instead of returning a partial list so a
 * broadcast is never silently sent to only part of its segment.
 */
public class AudienceResolutionException extends RuntimeException {

    public AudienceResolutionException(String message, Throwable cause) {
        super(message, cause);
    }

    public AudienceResolutionException(String message) {
        super(message);
    }
}
