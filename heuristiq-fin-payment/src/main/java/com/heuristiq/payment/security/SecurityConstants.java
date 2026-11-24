package com.heuristiq.paymentservice.security;

/** Fixed paths and names of the payment service's request authentication. */
public final class SecurityConstants {

    /** Internal (service-token) filter chain path pattern. */
    public static final String INTERNAL_PATH_PATTERN = "/api/v1/internal/**";
    /** Gateway webhooks, authenticated by their signature instead of a user token. */
    public static final String PAYMENT_WEBHOOK_PATH = "/api/v1/payment/payments/webhook";
    public static final String WALLET_TOPUP_WEBHOOK_PATH = "/api/v1/payment/wallet/topup/webhook";

    /** Prefix Spring Security expects on role authorities. */
    public static final String ROLE_AUTHORITY_PREFIX = "ROLE_";
    /** Authority granted to a verified backend service caller. */
    public static final String SERVICE_AUTHORITY = ROLE_AUTHORITY_PREFIX + AuthPrincipal.SERVICE_ROLE;
    /** User id recorded for a verified backend service caller. */
    public static final String INTERNAL_SERVICE_USER_ID = "internal-service";

    /** Gateway elevation headers. */
    public static final String ELEVATED_HEADER = "X-User-Elevated";
    public static final String GATEWAY_TOKEN_HEADER = "X-Gateway-Token";

    private SecurityConstants() {
    }
}
