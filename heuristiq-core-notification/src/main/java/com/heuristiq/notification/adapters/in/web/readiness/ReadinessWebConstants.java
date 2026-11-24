package com.railcab.nms.adapters.in.web.readiness;

import com.railcab.nms.adapters.in.web.WebApiConstants;

/** Wire values of NMS's internal (service-to-service) endpoints and of the readiness endpoint. */
public final class ReadinessWebConstants {

    /** Base of the service-credential-only endpoints, guarded by {@code NmsInternalServiceAuthFilter}. */
    public static final String INTERNAL_BASE_PATH = WebApiConstants.API_BASE_PATH + "/internal";
    public static final String READINESS_PATH = INTERNAL_BASE_PATH + "/readiness";

    public static final String PARAM_APPLICATION_CODE = "applicationCode";
    public static final String PARAM_TENANT_ID = "tenantId";

    /** 400 message when a parameter is missing or blank. */
    public static final String MSG_PARAMS_REQUIRED = "applicationCode and tenantId are required";

    private ReadinessWebConstants() {
    }
}
