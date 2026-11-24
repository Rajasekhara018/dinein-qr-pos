package com.railcab.dms.readiness.adapter.in;

/** Wire values of the internal readiness endpoint. */
public final class ReadinessWebConstants {

    /**
     * Under {@code /api/v1/dms/internal/}, which {@code InternalServiceAuthFilter} guards (service token or SERVICE JWT) and
     * the gateway refuses with 404.
     */
    public static final String READINESS_PATH = "/api/v1/dms/internal/readiness";

    public static final String PARAM_APPLICATION_CODE = "applicationCode";
    public static final String PARAM_TENANT_ID = "tenantId";

    /** 400 message when a parameter is missing or blank. */
    public static final String MSG_PARAMS_REQUIRED = "applicationCode and tenantId are required";

    private ReadinessWebConstants() {
    }
}
