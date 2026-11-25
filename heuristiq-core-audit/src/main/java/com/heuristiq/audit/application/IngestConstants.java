package com.heuristiq.audit.application;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * Fixed limits, patterns and name sets used when events are accepted, sanitized and rate limited. Values that
 * differ per environment (batch sizes, rate, IP masking) are configuration, see {@code AuditProperties}.
 */
public final class IngestConstants {

    private IngestConstants() {
    }

    /** Screen keys: lower-case dotted segments such as {@code payments.refunds}. */
    public static final Pattern SCREEN_KEY = Pattern.compile("^[a-z][a-z0-9_-]*(\\.[a-z0-9_-]+)*$");
    public static final int MAX_SCREEN_KEY = 100;

    /** Subject type and reason code: UPPER_SNAKE (letters, digits, underscore), starting with a letter. */
    public static final Pattern UPPER_SNAKE_CODE = Pattern.compile("^[A-Z][A-Z0-9_]*$");
    /** Subject id: identifier characters only, so it can never carry free text or query syntax. */
    public static final Pattern SUBJECT_ID = Pattern.compile("^[A-Za-z0-9_.:-]+$");
    public static final int MAX_SUBJECT_TYPE = 40;
    public static final int MAX_SUBJECT_ID = 128;
    public static final int MAX_REASON_CODE = 60;

    public static final Pattern EVENT_ID = Pattern.compile("^[A-Za-z0-9_-]{8,64}$");
    public static final Pattern TOKEN_LIKE_ID = Pattern.compile("^[A-Za-z0-9_.:\\-]{1,100}$");
    public static final Pattern DETAIL_KEY = Pattern.compile("^[A-Za-z][A-Za-z0-9_.-]{0,63}$");
    public static final Pattern SERVICE_NAME = Pattern.compile("^[A-Za-z0-9_.-]{1,64}$");
    public static final Pattern JWT_LIKE = Pattern.compile("eyJ[A-Za-z0-9_-]{5,}\\.[A-Za-z0-9_-]{5,}");
    public static final Pattern BEARER_LIKE = Pattern.compile("(?i)\\b(bearer|basic)\\s+[A-Za-z0-9._~+/=-]{8,}");
    public static final Pattern CAMEL_BOUNDARY = Pattern.compile("(?<=[a-z0-9])(?=[A-Z])");
    public static final Pattern NON_ALNUM = Pattern.compile("[^A-Za-z0-9]+");
    public static final Pattern IPV4 = Pattern.compile("^\\d{1,3}(\\.\\d{1,3}){3}$");
    public static final Set<String> METHODS = Set.of("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS");
    /** Words (split on separators and camelCase) that mark a detail key as a credential, so it is dropped. */
    public static final Set<String> SENSITIVE_WORDS = Set.of("password", "passwd", "pwd", "secret", "token", "otp",
            "pin", "cvv", "cvc", "card", "pan", "authorization", "cookie", "apikey", "signature", "aadhaar", "ssn",
            "bearer", "jwt", "credential", "credentials");
    /** Words that mark a detail key whose value is a person's contact and is masked. */
    public static final Set<String> MASKED_WORDS = Set.of("recipient", "email", "phone", "mobile", "msisdn");
    public static final String USER_TYPE_CUSTOMER = "CUSTOMER";
    public static final String USER_TYPE_DRIVER = "DRIVER";
    public static final String USER_TYPE_STAFF = "STAFF";
    public static final String USER_TYPE_SERVICE = "SERVICE";
    public static final String USER_TYPE_ANONYMOUS = "ANONYMOUS";
    /** Role name that service-to-service callers carry; reported as {@link #USER_TYPE_SERVICE}. */
    public static final String INTERNAL_SERVICE_ROLE = "INTERNAL_SERVICE";
    public static final Set<String> USER_TYPES = Set.of(USER_TYPE_CUSTOMER, USER_TYPE_DRIVER, USER_TYPE_STAFF,
            USER_TYPE_SERVICE, USER_TYPE_ANONYMOUS);

    public static final int MAX_DETAILS = 20;
    public static final int MAX_VALUE = 500;
    public static final int MAX_ROLES = 20;
    public static final int MAX_PRIVILEGES = 20;
    public static final long MAX_DURATION_MS = 24L * 60 * 60 * 1000;
    public static final String REDACTED_VALUE = "[redacted]";
    public static final String ROLE_PREFIX = "ROLE_";

    /** Source name stored on screen views, which come from the apps rather than a backend service. */
    public static final String CLIENT_SOURCE = "client-app";
    public static final Set<String> CLIENT_CHANNELS = Set.of("WEB", "ADMIN_WEB", "WALLET_WEB", "CUSTOMER_APP", "DRIVER_APP", "RESTAURANT_APP");
    /** Detail keys a screen view may carry; everything else is ignored. */
    public static final Set<String> SCREEN_DETAIL_KEYS = Set.of("appVersion", "platform", "previousScreenKey");
    public static final String INVALID_CHANNEL = "INVALID";

    /** Length of the rate-limit window. */
    public static final long WINDOW_MS = 60_000L;
    /** Number of tracked keys above which stale windows are swept. */
    public static final int SWEEP_AT = 10_000;

    /** Message when an ingest batch is null or empty. */
    public static final String EMPTY_BATCH_MESSAGE = "events must not be empty";

    /* Maximum stored lengths (characters) of the event fields, after control characters are removed. */
    public static final int MAX_EVENT_ID = 64;
    public static final int MAX_SERVICE_NAME = 64;
    public static final int MAX_METHOD = 10;
    public static final int MAX_STATUS_CODE = 999;
    public static final int MAX_ROLE = 64;
    public static final int MAX_USER_TYPE = 32;
    public static final int MAX_USER_ID = 64;
    public static final int MAX_FEATURE = 100;
    public static final int MAX_MODULE = 64;
    public static final int MAX_IP_ADDRESS = 64;
    public static final int MAX_USER_AGENT = 300;
    public static final int MAX_TENANT_ID = 64;
    /** Correlation and request ids. */
    public static final int MAX_TRACE_ID = 100;
    /** Path as received, before the query string is cut. */
    public static final int MAX_RAW_PATH = 2000;
    /** Path as stored. */
    public static final int MAX_PATH = 500;
    public static final int MAX_ENUM_NAME = 40;
    public static final int MAX_DETAIL_KEY = 64;
    public static final int MAX_CONTENT_TYPE = 100;
    /** Length of a raw IPv6 address in bytes. */
    public static final int IPV6_BYTES = 16;
}
