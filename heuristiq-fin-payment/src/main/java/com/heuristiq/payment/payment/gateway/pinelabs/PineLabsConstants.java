package com.heuristiq.payment.payment.gateway.pinelabs;

/** Pine Labs API paths, relative to the configured base URL. */
public final class PineLabsConstants {

    public static final String TOKEN_PATH = "/api/auth/v1/token";
    public static final String ORDERS_PATH = "/api/checkout/v1/orders";
    /** Order status path; the order id is appended. */
    public static final String ORDER_PATH = "/api/pay/v1/orders/";

    private PineLabsConstants() {
    }
}
