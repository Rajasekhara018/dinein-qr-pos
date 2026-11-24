package com.heuristiq.gateway.config;

import java.util.regex.Pattern;

/** Route values of Rider_WalletService (the shared credits ledger), which serves exactly the client-facing path. */
public final class WalletRouteConstants {

    /** What clients call and what the service serves: {@code /api/v1/wallet/<path>}. */
    public static final String GATEWAY_PREFIX = "/api/v1/wallet";

    /** Prefix of the docs and javadoc routes. */
    public static final String DOCS_PREFIX = "/api/rider/wallet";

    /** Eureka / Simple Discovery service id ({@code spring.application.name} upper-cased). */
    public static final String SERVICE_ID = "REALBOOM-WALLETSERVICE";

    /** Local port when neither discovery nor a static URL is configured. */
    public static final int LOCAL_PORT = 9121;

    public static final String ROUTE_ID = "rider-wallet-api";
    public static final String DOCS_ROUTE_ID = "rider-wallet-docs";
    public static final String JAVADOC_ROUTE_ID = "rider-wallet-javadoc";
    public static final String CIRCUIT_BREAKER = "rider-wallet-cb";

    /** Suffix of the circuit-breaker fallback ({@code forward:/fallback/rider-wallet}); also the catalog service slug. */
    public static final String FALLBACK = "rider-wallet";

    /**
     * The service's service-token-only endpoints ({@code /api/v1/wallet/internal/**}, used by app backends to consume,
     * hold and refund credits). Matched against the normalised path; the gateway answers 404 for them.
     */
    public static final Pattern INTERNAL_PATH = Pattern.compile("^/api/v\\d+/wallet/internal(/|$)");

    private WalletRouteConstants() {
    }
}
