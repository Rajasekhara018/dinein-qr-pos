package com.heuristiq.identity.readiness.config;

import java.util.Map;

import com.heuristiq.identity.readiness.adapter.out.ReadinessAuthMode;
import com.heuristiq.identity.readiness.domain.ReadinessSource;

/**
 * Built-in defaults of each service's readiness endpoint, used when {@code railcab.uam.readiness.services.<name>.*} leaves a
 * value out. Base URLs are the services' local development ports; deployments set them through environment variables.
 */
public final class ReadinessConfigConstants {

    /** Name of the bounded executor's threads. */
    public static final String THREAD_NAME_PREFIX = "readiness-";

    /** Built-in default per service. */
    public static final Map<ReadinessSource, Default> DEFAULTS = Map.of(
            ReadinessSource.WALLET, new Default("http://localhost:9121", "/api/v1/wallet/internal/readiness", ReadinessAuthMode.SERVICE_TOKEN),
            ReadinessSource.REPORTS, new Default("http://localhost:9122", "/api/v1/reports/internal/readiness", ReadinessAuthMode.SERVICE_TOKEN),
            ReadinessSource.ONBOARDING, new Default("http://localhost:9112", "/api/v1/internal/readiness", ReadinessAuthMode.SERVICE_JWT),
            ReadinessSource.DMS, new Default("http://localhost:9104", "/api/v1/dms/internal/readiness", ReadinessAuthMode.SERVICE_TOKEN),
            ReadinessSource.NMS, new Default("http://localhost:9111", "/api/v1/nms/internal/readiness", ReadinessAuthMode.SERVICE_TOKEN));

    private ReadinessConfigConstants() {
    }

    /**
     * Default endpoint of one service.
     *
     * @param baseUrl service root
     * @param path    readiness path
     * @param auth    credential it expects
     */
    public record Default(String baseUrl, String path, ReadinessAuthMode auth) {
    }
}
