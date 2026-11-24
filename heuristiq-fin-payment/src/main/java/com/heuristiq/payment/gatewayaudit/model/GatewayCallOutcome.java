package com.heuristiq.paymentservice.gatewayaudit.model;

/** Result of one outbound gateway call. */
public enum GatewayCallOutcome {
    SUCCESS,
    FAILURE,
    TIMEOUT
}
