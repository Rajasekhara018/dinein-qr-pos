package com.heuristiq.uam.readiness.domain;

import java.util.Map;

/**
 * One fact as a service (or Rider_UAM itself) reports it: no severity, phase or order (those come from the profile).
 *
 * @param id         stable UPPER_SNAKE id prefixed by the service, e.g. {@code WALLET_APP_REGISTERED}
 * @param status     the result
 * @param messageKey translation key owned by Rider_Web, {@code readiness.<service>.<check>.<status>}
 * @param params     string values for the message; never null
 * @param fixPath    Rider_Web route that fixes it, or null (always null for PASS and NOT_APPLICABLE)
 */
public record ReadinessCheck(String id, ReadinessStatus status, String messageKey, Map<String, String> params, String fixPath) {

    public ReadinessCheck {
        params = params == null ? Map.of() : Map.copyOf(params);
    }
}
