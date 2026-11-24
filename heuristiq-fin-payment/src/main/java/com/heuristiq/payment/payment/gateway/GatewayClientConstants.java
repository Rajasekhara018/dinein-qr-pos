package com.heuristiq.paymentservice.payment.gateway;

import java.time.Duration;

/** Fixed values shared by the payment-gateway clients. */
public final class GatewayClientConstants {

    /** Upper bound of one blocking gateway call. */
    public static final Duration CALL_TIMEOUT = Duration.ofSeconds(30);

    /** Normalised status a status-check client reports, whatever the gateway's own vocabulary. */
    public static final String RESULT_SUCCESS = "SUCCESS";
    public static final String RESULT_PENDING = "PENDING";
    public static final String RESULT_FAILED = "FAILED";

    private GatewayClientConstants() {
    }
}
