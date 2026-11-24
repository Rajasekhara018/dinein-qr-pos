package com.heuristiq.notification.infrastructure.config;

import java.time.Duration;
import java.util.List;

import com.heuristiq.notification.adapters.in.web.WebApiConstants;
import com.heuristiq.notification.adapters.in.web.callback.CallbackConstants;
import com.heuristiq.notification.adapters.in.web.readiness.ReadinessWebConstants;

/**
 * Fixed values of NMS's bootstrap/wiring layer: request headers and MDC keys, security-exempt paths, JWT claim names,
 * the STOMP endpoint and broker prefixes, and the fixed resilience limits of the audit publisher. Tunable values are
 * not here; they come from {@link NmsProperties} or {@code @Value} properties.
 */
final class NmsConfigConstants {

    // --- headers and logging context ---
    /** Correlation header; the same one the API gateway's CorrelationIdFilter sets. */
    static final String HEADER_CORRELATION_ID = "X-Correlation-ID";
    /** MDC key holding the request's correlation id (referenced by the log pattern). */
    static final String MDC_CORRELATION_ID = "correlationId";
    /** Shared internal API key header for service-to-service calls. */
    static final String HEADER_INTERNAL_API_KEY = "X-NMS-API-Key";
    /** Shared platform service token header, accepted only on the internal ({@link #INTERNAL_PATH_PREFIX}) endpoints. */
    static final String HEADER_SERVICE_TOKEN = "X-Rider-Service-Token";
    /** Organization header, honoured only for internal-key callers and service tokens without a tenant claim. */
    static final String HEADER_TENANT_ID = "X-Tenant-Id";
    /** Prefix of an {@code Authorization} header carrying a JWT. */
    static final String BEARER_PREFIX = "Bearer ";

    // --- principals and roles ---
    /** User id of a caller authenticated with the internal API key. */
    static final String INTERNAL_USER = "internal-service";
    /** Roles given to a caller authenticated with the internal API key. */
    static final List<String> INTERNAL_ROLES = List.of("NMS_ADMIN", "NMS_CLIENT");
    /** Role assumed when a token carries no role claim. */
    static final String DEFAULT_CLIENT_ROLE = "NMS_CLIENT";
    /** Role marking a backend service token. */
    static final String SERVICE_ROLE = "SERVICE";
    /** Authority only {@link NmsInternalServiceAuthFilter} grants; the internal endpoints demand it. */
    static final String SERVICE_AUTHORITY = "SERVICE_CREDENTIAL";
    /** Spring Security authority prefix. */
    static final String ROLE_PREFIX = "ROLE_";
    /** User id used when a verified token has no subject. */
    static final String DEFAULT_JWT_SUBJECT = "jwt-client";

    // --- JWT claims ---
    static final String CLAIM_TENANT_ID = "tenantId";
    static final String CLAIM_ROLES = "roles";
    static final String CLAIM_AUTHORITIES = "authorities";
    static final String CLAIM_SCOPE = "scope";
    static final String CLAIM_SCP = "scp";
    /** Separator of roles inside a single string claim. */
    static final String ROLE_CLAIM_SEPARATOR_REGEX = "[,\\s]+";

    // --- error response ---
    static final String UNAUTHORIZED_MESSAGE = "Unauthorized";

    // --- paths ---
    /** STOMP/SockJS endpoint; not a controller, so ApiVersionConfig's /api/v1 prefix does not reach it. */
    static final String IN_APP_WS_PATH = WebApiConstants.IN_APP_WS_PATH;
    static final String IN_APP_WS_PATTERN = IN_APP_WS_PATH + "/**";
    static final String CALLBACKS_PATH_PREFIX = CallbackConstants.BASE_PATH + "/";
    static final String CALLBACKS_PATTERN = CallbackConstants.BASE_PATH + "/**";
    /** Service-credential-only endpoints (not opened by the gateway-injected NMS API key). */
    static final String INTERNAL_PATH_PREFIX = ReadinessWebConstants.INTERNAL_BASE_PATH + "/";
    static final String INTERNAL_PATTERN = ReadinessWebConstants.INTERNAL_BASE_PATH + "/**";
    static final String ACTUATOR_PATH_PREFIX = "/actuator/";
    static final String ACTUATOR_PATTERN = "/actuator/**";
    static final String JAVADOC_PATH = "/javadoc";
    static final String JAVADOC_PATTERN = "/javadoc/**";
    static final String API_DOCS_PATH = "/v3/api-docs";
    static final String API_DOCS_PATTERN = "/v3/api-docs/**";
    static final String SWAGGER_UI_PATH = "/swagger-ui";
    static final String SWAGGER_UI_PATTERN = "/swagger-ui/**";
    static final String SWAGGER_UI_HTML = "/swagger-ui.html";

    // --- STOMP broker ---
    static final String WS_ALLOWED_ORIGIN_PATTERN = "*";
    static final String BROKER_TOPIC_PREFIX = "/topic";
    static final String BROKER_QUEUE_PREFIX = "/queue";
    static final String APP_DESTINATION_PREFIX = "/app";
    static final String USER_DESTINATION_PREFIX = "/user";

    // --- resilience ---
    static final String AUDIT_CALLER_NAME = "audit-service";
    static final String UAM_CALLER_NAME = "uam-service";
    static final int AUDIT_BREAKER_WINDOW = 10;
    static final int AUDIT_BREAKER_MIN_CALLS = 5;
    static final float AUDIT_BREAKER_FAILURE_PERCENT = 50f;
    static final Duration AUDIT_BREAKER_OPEN = Duration.ofSeconds(30);
    static final int AUDIT_MAX_CONCURRENT_CALLS = 2;

    private NmsConfigConstants() {
    }
}
