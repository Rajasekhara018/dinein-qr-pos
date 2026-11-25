package com.heuristiq.payment.payment;

import com.heuristiq.payment.payment.gateway.model.PaymentGatewayType;
import com.heuristiq.payment.payment.model.PaymentStatus;

import java.math.BigDecimal;
import java.util.List;

/** Fixed limits of the payment feature. */
public final class PaymentConstants {

    /** Maximum stored length of a payment or refund failure reason. */
    public static final int MAX_FAILURE_REASON = 500;
    /** Maximum length of a caller-supplied refund reference. */
    public static final int MAX_REFUND_REFERENCE = 100;
    /** Hard cap on the admin payment list page size. */
    public static final int ADMIN_MAX_PAGE_SIZE = 100;
    /** Default allowed difference between a requested payment amount and the ride fare (app.payment.fare-verification.tolerance-rupees). */
    public static final BigDecimal FARE_TOLERANCE_RUPEES = new BigDecimal("0.01");

    /** Payment states the status poll and on-demand reconcile still ask the gateway about. */
    public static final List<PaymentStatus> POLLABLE_STATUSES = List.of(PaymentStatus.INITIATED, PaymentStatus.AUTHORIZED);
    /** Gateways the status poll covers (CASH has no gateway and is never polled). */
    public static final List<PaymentGatewayType> POLLABLE_GATEWAYS = List.of(
            PaymentGatewayType.PAYU, PaymentGatewayType.TOUCAN, PaymentGatewayType.PINELABS, PaymentGatewayType.RAZORPAY);
    /** Lifecycle source recorded for a transition made by the scheduled status poll. */
    public static final String LIFECYCLE_SOURCE_STATUS_POLL = "STATUS_POLL";
    /** Lifecycle source recorded for a transition made by an on-demand reconcile. */
    public static final String LIFECYCLE_SOURCE_RECONCILE = "RECONCILE";
    /** Keyset start for the status poll's candidate pages: every payment id sorts after it. */
    public static final String POLL_KEYSET_START = "";

    /** Longest id (ride, customer, driver) a direct collection accepts. */
    public static final int MAX_ID = 64;
    /** Internal path of the direct-collection record (Rider_RideService, service token only). */
    public static final String DIRECT_COLLECTIONS_PATH = "/api/v1/internal/payments/direct-collections";
    /** Channels a direct collection may name: cash, or UPI to the driver's own UPI id. */
    public static final String DIRECT_COLLECTION_CHANNEL_PATTERN = "^(CASH|UPI)$";
    public static final String DIRECT_COLLECTION_CHANNEL_MESSAGE = "channel must be CASH or UPI";
    /** The UPI channel of a direct collection (recorded as method UPI_DIRECT). */
    public static final String DIRECT_COLLECTION_CHANNEL_UPI = "UPI";
    /** Idempotency key of a ride's direct collection: one record per ride. */
    public static final String DIRECT_COLLECTION_KEY_PREFIX = "direct-collection:";
    /** Invoice and receipt number prefixes of a captured payment. */
    public static final String INVOICE_PREFIX = "INV-";
    /** A direct collection must name its organization (X-Tenant-Id with the service token). */
    public static final String MSG_DIRECT_COLLECTION_TENANT_REQUIRED = "X-Tenant-Id is required to record a direct collection";
    /** The ride's collection is already recorded for another organization. */
    public static final String MSG_DIRECT_COLLECTION_OTHER_TENANT = "This ride's collection belongs to another organization";
    /** A direct collection is recorded by the ride service only, never created through the payments API. */
    public static final String MSG_UPI_DIRECT_NOT_CREATABLE =
            "UPI_DIRECT payments are recorded when the driver confirms the collection; pay the driver by UPI instead";

    private PaymentConstants() {
    }
}
