package com.heuristiq.identity.readiness.adapter.out;

/** Wire values of the calls to the services' internal readiness endpoints. */
public final class ReadinessAdapterConstants {

    /** Query parameter: the application code. */
    public static final String PARAM_APPLICATION_CODE = "applicationCode";

    /** Query parameter: the organization id. */
    public static final String PARAM_TENANT_ID = "tenantId";

    /** Shared service-to-service credential header (wallet, reports, DMS, NMS). */
    public static final String SERVICE_TOKEN_HEADER = "X-Rider-Service-Token";

    /** Correlation id header forwarded on every call. */
    public static final String CORRELATION_ID_HEADER = "X-Correlation-ID";

    /** MDC key holding the inbound request's correlation id. */
    public static final String CORRELATION_ID_MDC_KEY = "correlationId";

    /** Bearer scheme prefix of the Authorization header. */
    public static final String BEARER_PREFIX = "Bearer ";

    /** Claim carrying the role in a service JWT (Rider_OnboardingService reads it). */
    public static final String ROLE_CLAIM = "role";

    /** Role value of the backend service identity. */
    public static final String SERVICE_ROLE = "SERVICE";

    /** Subject of the service JWTs Rider_UAM mints. */
    public static final String SERVICE_SUBJECT = "rider-uam";

    /** Envelope field of the standard response ({@code CommonResponseObject}). */
    public static final String ENVELOPE_RESPONSE_OBJECT = "responseObject";

    /** Envelope field of Rider_OnboardingService's {@code {status, data, message}} response. */
    public static final String ENVELOPE_DATA = "data";

    /** Report fields. */
    public static final String FIELD_CHECKS = "checks";
    public static final String FIELD_ID = "id";
    public static final String FIELD_STATUS = "status";
    public static final String FIELD_MESSAGE_KEY = "messageKey";
    public static final String FIELD_PARAMS = "params";
    public static final String FIELD_FIX_PATH = "fixPath";

    /** Name prefix of each service's resilient caller (circuit breaker, bulkhead, retry). */
    public static final String CALLER_NAME_PREFIX = "readiness-";

    private ReadinessAdapterConstants() {
    }
}
