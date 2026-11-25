package com.heuristiq.identity.readiness.domain;

/** Fixed values of the readiness feature's domain: message key parts, parameter names and reason codes. */
public final class ReadinessConstants {

    /** First segment of every readiness message key. */
    public static final String MESSAGE_KEY_ROOT = "readiness";

    /** Separator between the parts of a message key. */
    public static final String MESSAGE_KEY_SEPARATOR = ".";

    /** Message parameter carrying why a step could not be evaluated, or why it failed when that helps. */
    public static final String PARAM_REASON = "reason";

    /** Message parameter carrying the service slug on the connectivity steps. */
    public static final String PARAM_SERVICE = "service";

    /** The service answered with a body that is not a readiness report. */
    public static final String REASON_MALFORMED_RESPONSE = "MALFORMED_RESPONSE";

    /** The service answered but did not report this check. */
    public static final String REASON_NOT_REPORTED = "NOT_REPORTED";

    /** The overall time budget ran out before the service answered. */
    public static final String REASON_TIMEOUT = "TIMEOUT";

    /** The call could not be started: too many readiness calls in flight. */
    public static final String REASON_BUSY = "BUSY";

    /** The request thread was interrupted while waiting. */
    public static final String REASON_INTERRUPTED = "INTERRUPTED";

    /** An unexpected error while evaluating (logged with details). */
    public static final String REASON_ERROR = "ERROR";

    /** Prefix of the reason for a 4xx answer, followed by the status code (e.g. {@code HTTP_401}). */
    public static final String REASON_HTTP_PREFIX = "HTTP_";

    /** UAM has no application row for the code (it was never created on the platform). */
    public static final String REASON_NOT_REGISTERED = "NOT_REGISTERED";

    /** Query parameter Rider_Web reads to open a fix screen scoped to one application. */
    public static final String FIX_PATH_APPLICATION_PARAM = "?application=";

    /** Rider_Web route of the applications screen. */
    public static final String FIX_PATH_APPLICATIONS = "/access-management/applications";

    private ReadinessConstants() {
    }
}
