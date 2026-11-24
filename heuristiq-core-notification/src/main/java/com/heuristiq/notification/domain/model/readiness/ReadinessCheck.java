package com.heuristiq.notification.domain.model.readiness;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * One readiness fact for one application and one organization.
 *
 * @param id         stable UPPER_SNAKE id prefixed by the service (never renamed once released)
 * @param status     the outcome
 * @param messageKey {@code readiness.<service>.<check>.<status lower case>}; Rider_Web owns the texts
 * @param params     string values for the message (no personal data, no secrets); never null
 * @param fixPath    the Rider_Web route that fixes it; null for PASS and NOT_APPLICABLE
 */
public record ReadinessCheck(String id, ReadinessStatus status, String messageKey, Map<String, String> params,
                             String fixPath) {

    public ReadinessCheck {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(messageKey, "messageKey");
        Map<String, String> copy = new LinkedHashMap<>();
        if (params != null) {
            params.forEach((k, v) -> copy.put(Objects.requireNonNull(k, "param name"), Objects.requireNonNull(v, "param value")));
        }
        params = Collections.unmodifiableMap(copy);
    }

    /**
     * Builds a check whose message key is {@code messageKeyBase + "." + status} and whose fix path is kept only when there is
     * something to fix (not for PASS or NOT_APPLICABLE).
     */
    public static ReadinessCheck of(String id, String messageKeyBase, ReadinessStatus status, Map<String, String> params,
                                    String fixPath) {
        boolean fixable = status != ReadinessStatus.PASS && status != ReadinessStatus.NOT_APPLICABLE;
        return new ReadinessCheck(id, status, messageKeyBase + "." + status.messageKeySuffix(), params,
                fixable ? fixPath : null);
    }
}
