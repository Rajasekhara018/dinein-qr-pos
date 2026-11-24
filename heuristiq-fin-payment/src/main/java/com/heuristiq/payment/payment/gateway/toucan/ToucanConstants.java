package com.heuristiq.paymentservice.payment.gateway.toucan;

/** Toucan API paths, relative to the configured base URL. */
public final class ToucanConstants {

    public static final String SESSION_PATH = "/api/auth/getpaymentsession";
    /** Status path; also sent as the request's messageID. */
    public static final String STATUS_PATH = "/api/pay/v1/checkStatus";

    private ToucanConstants() {
    }
}
