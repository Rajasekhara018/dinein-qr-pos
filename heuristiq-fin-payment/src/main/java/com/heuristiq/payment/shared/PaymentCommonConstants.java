package com.heuristiq.payment.shared;

/** Fixed wire values shared by more than one feature of the payment service. */
public final class PaymentCommonConstants {

    /** Shared service-to-service credential header (inbound check, outbound calls, audit ingest). */
    public static final String SERVICE_TOKEN_HEADER = "X-Rider-Service-Token";
    /** Organization a backend service acts for; honoured only with a valid service token. */
    public static final String TENANT_HEADER = "X-Tenant-Id";
    /** Scheme prefix of a bearer {@code Authorization} header. */
    public static final String BEARER_PREFIX = "Bearer ";

    /** Razorpay webhook headers (payment and wallet top-up webhooks). */
    public static final String RAZORPAY_SIGNATURE_HEADER = "X-Razorpay-Signature";
    public static final String RAZORPAY_EVENT_ID_HEADER = "X-Razorpay-Event-Id";

    /** Currency of every payment, top-up and gateway amount. */
    public static final String DEFAULT_CURRENCY = "INR";
    /** Length of an ISO 4217 currency code, the width of every <amount>_currency column. */
    public static final int CURRENCY_CODE_LENGTH = 3;

    private PaymentCommonConstants() {
    }
}
