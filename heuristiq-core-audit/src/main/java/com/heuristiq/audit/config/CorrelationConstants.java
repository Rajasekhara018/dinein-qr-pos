package com.railcab.audit.config;

/**
 * Header and MDC key of the correlation id.
 */
public final class CorrelationConstants {

    private CorrelationConstants() {
    }

    public static final String HEADER = "X-Correlation-ID";

    public static final String MDC_KEY = "correlationId";

}
