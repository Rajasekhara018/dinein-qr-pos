package com.heuristiq.paymentservice.gatewayaudit.model;

/** The kind of outbound gateway call that was logged. */
public enum GatewayCallOperation {
    CREATE_SESSION,
    REFUND,
    STATUS_CHECK
}
