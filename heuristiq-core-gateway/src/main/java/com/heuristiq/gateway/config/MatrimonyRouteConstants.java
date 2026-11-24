package com.heuristiq.gateway.config;

/**
 * Client-facing path of the matrimony routes. matrimonial-api serves the very same {@code /api/v1/matrimony/**} (and its
 * STOMP socket at {@code /api/v1/matrimony/ws-raw}), like every other Rider service, so the gateway forwards the path as it is.
 */
public final class MatrimonyRouteConstants {

    /** What clients call, and what matrimonial-api serves: {@code /api/v1/matrimony/<path>}. */
    public static final String GATEWAY_PREFIX = "/api/v1/matrimony";

    private MatrimonyRouteConstants() {
    }
}
