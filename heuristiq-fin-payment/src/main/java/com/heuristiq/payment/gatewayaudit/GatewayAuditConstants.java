package com.heuristiq.payment.gatewayaudit;

import com.heuristiq.payment.shared.PaymentCommonConstants;

/**
 * Fixed values of the payment-gateway call log and the audit outbox. Per-environment values
 * (URL, batch size, intervals, retention) live in {@code AuditPublishProperties}, not here.
 */
public final class GatewayAuditConstants {

    /** {@code sourceService} reported to the audit service. */
    public static final String SOURCE_SERVICE = "payment-service";
    /** Audit event type for gateway traffic. */
    public static final String EVENT_TYPE = "PAYMENT_GATEWAY";
    /** Provider name recorded for Razorpay webhook deliveries. */
    public static final String PROVIDER_RAZORPAY = "RAZORPAY";
    public static final String FEATURE = "rider-payment";
    public static final String MODULE = "PAYMENTS";
    public static final String USER_TYPE_SERVICE = "SERVICE";
    public static final String CHANNEL_SERVICE = "SERVICE";

    /** Audit outcomes (the audit service accepts SUCCESS, FAILURE, DENIED). */
    public static final String AUDIT_SUCCESS = "SUCCESS";
    public static final String AUDIT_FAILURE = "FAILURE";

    /** Operation name used for webhook deliveries (gateway calls use {@code GatewayCallOperation}). */
    public static final String OPERATION_WEBHOOK = "WEBHOOK";
    /** Failure reason recorded when a webhook signature does not verify. */
    public static final String REASON_SIGNATURE_INVALID = "SIGNATURE_INVALID";

    /** Detail attribute names (the audit service allows at most 20, none credential-like). */
    public static final String D_GATEWAY = "gateway";
    public static final String D_OPERATION = "operation";
    public static final String D_GATEWAY_REF = "gatewayRef";
    public static final String D_PAYMENT_ID = "paymentId";
    public static final String D_FAILURE_REASON = "failureReason";
    public static final String D_ATTEMPT = "attempt";
    public static final String D_REFERENCE = "reference";
    public static final String D_ERROR_CLASS = "errorClass";
    public static final String D_EVENT_TYPE = "eventType";
    public static final String D_WEBHOOK_OUTCOME = "webhookOutcome";
    public static final String D_SIGNATURE_VALID = "signatureValid";
    public static final String D_CALL_OUTCOME = "callOutcome";

    /** Bounds for stored text. */
    public static final int MAX_FAILURE_REASON = 200;
    public static final int MAX_ERROR_CLASS = 128;
    public static final int MAX_REFERENCE = 128;
    public static final int MAX_EVENT_TYPE = 64;

    /** Upper bound of rows the admin read returns for one payment. */
    public static final int MAX_ADMIN_ROWS = 500;

    /** Audit ingest endpoint and credential header. */
    public static final String INGEST_PATH = "/api/v1/audit/internal/events";
    public static final String SERVICE_TOKEN_HEADER = PaymentCommonConstants.SERVICE_TOKEN_HEADER;

    /** Metric names. */
    public static final String METRIC_RECORD_FAILURES = "payment.gateway.audit.record.failures";
    public static final String METRIC_PUBLISHED = "payment.gateway.audit.published";
    public static final String METRIC_PUBLISH_FAILURES = "payment.gateway.audit.publish.failures";

    /** Scheduler lock names. */
    public static final String LOCK_DRAIN = "payment-audit-outbox-drain";
    public static final String LOCK_PURGE = "payment-audit-outbox-purge";

    private GatewayAuditConstants() {
    }
}
