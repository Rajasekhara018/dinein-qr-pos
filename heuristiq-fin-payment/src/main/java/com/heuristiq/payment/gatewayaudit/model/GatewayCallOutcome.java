package com.heuristiq.payment.gatewayaudit.model;

/** Result of one outbound gateway call. */
public enum GatewayCallOutcome {
    SUCCESS,
    FAILURE,
    TIMEOUT
}
