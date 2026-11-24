package com.heuristiq.gateway.audit;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Fixed names, patterns, vocabularies and paths of the access-audit publisher. Per-environment values are configuration, see {@code AuditProperties}.
 */
public final class AuditConstants {

    private AuditConstants() {
    }

    /** Replacement for a redacted value. */
    public static final String MASK = "***";

    public static final Pattern JWT_LIKE = Pattern.compile("eyJ[A-Za-z0-9_-]{5,}\\.[A-Za-z0-9_-]{5,}(\\.[A-Za-z0-9_-]*)?");

    public static final Pattern BEARER_LIKE = Pattern.compile("(?i)\\b(bearer|basic)\\s+[A-Za-z0-9._~+/=-]{8,}");

    /** key = value pairs in JSON-ish or key=value text; the value may be an unterminated quoted string. */
    public static final Pattern PAIR = Pattern.compile(
            "([\"']?)([A-Za-z0-9_.-]{1,64})\\1(\\s*[:=]\\s*)(\"(?:[^\"\\\\]|\\\\.)*\"?|'(?:[^'\\\\]|\\\\.)*'?|[^,&}\\]\\s\"']+)");

    public static final Pattern CAMEL_BOUNDARY = Pattern.compile("(?<=[a-z0-9])(?=[A-Z])");

    public static final Set<String> SECRET_EXACT = Set.of("password", "passwd", "pwd", "pin", "mpin", "upipin", "otp",
            "token", "accesstoken", "refreshtoken", "idtoken", "secret", "clientsecret", "apikey", "cvv", "cvv2", "cvc",
            "authorization", "signature", "passcode", "privatekey", "sessionid", "cookie");

    public static final Set<String> CARD_ACCOUNT = Set.of("cardnumber", "cardno", "accountnumber", "accountno",
            "bankaccount", "bankaccountnumber", "debitcardnumber", "creditcardnumber");

    public static final Set<String> CONTACT = Set.of("phone", "phonenumber", "mobile", "mobilenumber", "msisdn",
            "email", "emailid", "emailaddress", "recipient");

    public static final Set<String> VPA = Set.of("vpa", "upi", "upiid", "upiaddress", "payeevpa", "payervpa");

    public static final Pattern SEPARATORS = Pattern.compile("[_.\\-\\s]");

    public static final int KIND_CACHE_MAX = 4096;

    /** Default list of sensitive path patterns whose bodies are never captured (same default as the audit service). */
    public static final List<String> DEFAULT_SKIP_BODY_PATHS = List.of("/**/identity/auth/**", "/**/auth/**",
            "/**/*otp*", "/**/*otp*/**", "/**/*password*", "/**/*password*/**", "/**/*token*", "/**/*token*/**",
            "/**/*card*", "/**/*card*/**");

    public static final Pattern ID_SEGMENT = Pattern.compile(
            "^(\\d+|[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}|[0-9a-fA-F]{24})$");

    public static final Pattern INTERNAL_AUDIT = Pattern.compile("(^|/)audit/internal(/|$)");

    /** Optional header the web and mobile apps set to name their surface. */
    public static final String APP_CHANNEL_HEADER = "X-App-Channel";

    public static final Set<String> CLIENT_CHANNELS = Set.of("WEB", "ADMIN_WEB", "CUSTOMER_APP", "DRIVER_APP");

    public static final String SOURCE = "api-gateway";

    public static final String PATH = "/api/v1/audit/internal/events";

    /** Circuit breaker instance name (tune it with {@code resilience4j.circuitbreaker.instances.audit-publisher.*}). */
    public static final String CIRCUIT_BREAKER = "audit-publisher";

    public static final String LOCAL_URL = "http://localhost:9106";

    /** Filter order: just ahead of PrivilegeBasedAccessFilter (-79). */
    public static final int ORDER = -80;

    public static final int MAX_HEADERS = 60;

    /** How long the batcher waits for its last flush while the gateway shuts down. */
    public static final Duration SHUTDOWN_FLUSH_TIMEOUT = Duration.ofSeconds(3);

    public static final String EVENT_TYPE_ACCESS = "ACCESS";
    public static final String OUTCOME_SUCCESS = "SUCCESS";
    public static final String OUTCOME_FAILURE = "FAILURE";
    public static final String OUTCOME_DENIED = "DENIED";
    public static final String DETAIL_DENY_REASON = "denyReason";
    public static final String DENY_REASON_PRIVILEGE = "PRIVILEGE";
    public static final String DETAIL_QUERY = "query";
    public static final String HEADER_CORRELATION_ID = "X-Correlation-Id";
    public static final String HEADER_FORWARDED_FOR = "X-Forwarded-For";
    public static final String HEADER_SERVICE_TOKEN = "X-Rider-Service-Token";
    public static final String USER_TYPE_SERVICE = "SERVICE";
    public static final String USER_TYPE_CUSTOMER = "CUSTOMER";
    public static final String USER_TYPE_DRIVER = "DRIVER";
    public static final String USER_TYPE_STAFF = "STAFF";
    public static final String USER_TYPE_ANONYMOUS = "ANONYMOUS";
    public static final String CHANNEL_SERVICE = "SERVICE";
    public static final String CHANNEL_WEB = "WEB";
    public static final String CHANNEL_ADMIN_WEB = "ADMIN_WEB";
    public static final String CHANNEL_CUSTOMER_APP = "CUSTOMER_APP";
    public static final String CHANNEL_DRIVER_APP = "DRIVER_APP";
    public static final Set<String> MOBILE_UA_TOKENS = Set.of("dart/", "okhttp", "cfnetwork");
    public static final String BROWSER_UA_TOKEN = "mozilla";
}
