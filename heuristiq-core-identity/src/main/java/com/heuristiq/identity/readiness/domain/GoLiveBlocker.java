package com.heuristiq.uam.readiness.domain;

import java.util.Map;

/**
 * One blocking step as reported to the person who tried to switch an application on, and in alerts.
 *
 * @param stepId     check id, e.g. {@code WALLET_APP_REGISTERED}
 * @param status     FAIL or UNKNOWN
 * @param messageKey translation key owned by Rider_Web (the step's own readiness key)
 * @param params     message parameters of the step
 * @param fixPath    Rider_Web route that fixes it, or null
 */
public record GoLiveBlocker(String stepId, ReadinessStatus status, String messageKey, Map<String, String> params, String fixPath) {

    public GoLiveBlocker {
        params = params == null ? Map.of() : Map.copyOf(params);
    }
}
