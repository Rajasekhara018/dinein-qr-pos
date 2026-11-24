package com.heuristiq.paymentservice.salesettlement.domain;

/**
 * Fixed values of the Payment -> Wallet sale settlement (docs/FRANCHISE_PLAN.md step 8, decision D7).
 *
 * <p><b>Which sale a payment pays for</b> (the wallet's sale event id), derived from the payment's own reference fields by
 * {@link SaleEventIds}:
 * <table>
 *   <tr><th>Payment</th><th>Sale event id</th><th>Booked by</th></tr>
 *   <tr><td>{@code sourceService} empty, {@code rideId} set (a ride)</td><td>{@code ride-completed:<rideId>}</td>
 *       <td>Rider_RideService ({@code RideSaleLedgerService}) through Rider_FranchiseService</td></tr>
 *   <tr><td>{@code sourceService = matrimonial-api}, {@code sourceReference = <paymentTransactionId>}</td>
 *       <td>{@code MATRIMONY:SALE:<paymentTransactionId>}</td><td>matrimonial-api ({@code MatrimonySaleKeys})</td></tr>
 *   <tr><td>{@code sourceService = food-delivery-platform}, {@code sourceReference = <orderId>}</td>
 *       <td>{@code food-order-delivered:<orderId>}</td><td>Royal Food ({@code FoodSalesConstants}); reserved: Royal Food
 *       takes its payments through its own gateway integration today, so no payment here carries it yet</td></tr>
 *   <tr><td>any other {@code sourceService}</td><td>none: nothing is settled</td><td></td></tr>
 * </table>
 * Only gateway payments settle a sale here (method RAZORPAY with a gateway). A CASH payment's sale settles when the driver's
 * cash reaches the company, inside Rider_WalletService.
 */
public final class SaleSettlementConstants {

    private SaleSettlementConstants() {
    }

    /** Ride sale event id prefix; must match Rider_RideService {@code RideEventConstants.RIDE_COMPLETED_SALE_PREFIX}. */
    public static final String RIDE_SALE_PREFIX = "ride-completed:";
    /** {@code sourceService} of a matrimony payment; must match matrimonial-api {@code ServiceCallConstants.SERVICE_NAME}. */
    public static final String MATRIMONY_SOURCE_SERVICE = "matrimonial-api";
    /** Matrimony gateway sale event id prefix; must match matrimonial-api {@code MatrimonySaleKeys.gatewaySaleEventId}. */
    public static final String MATRIMONY_SALE_PREFIX = "MATRIMONY:SALE:";
    /** {@code sourceService} a Royal Food payment would carry. */
    public static final String FOOD_SOURCE_SERVICE = "food-delivery-platform";
    /** Food sale event id prefix; must match Royal Food {@code FoodSalesConstants.EVENT_ID_PREFIX}. */
    public static final String FOOD_SALE_PREFIX = "food-order-delivered:";

    /** Wallet idempotency key of a settlement, as docs/WALLET_API_CONTRACT.md asks: {@code sale-settle:<eventId>}. */
    public static final String SETTLE_KEY_PREFIX = "sale-settle:";
    /** Wallet idempotency key of a cancellation: {@code sale-cancel:<eventId>}. */
    public static final String CANCEL_KEY_PREFIX = "sale-cancel:";

    /** Wallet error code: the sale was already settled or cancelled, so there is nothing left to do. */
    public static final String CODE_SALE_NOT_PENDING = "SALE_NOT_PENDING";
    /** Wallet error code of a request it refuses as such (it will never succeed as sent). */
    public static final String CODE_VALIDATION_ERROR = "VALIDATION_ERROR";
    /** Wallet error code when no organization reached it. */
    public static final String CODE_TENANT_REQUIRED = "TENANT_REQUIRED";

    /** Outcomes recorded on a finished request. */
    public static final String OUTCOME_SETTLED = "SETTLED";
    public static final String OUTCOME_CANCELLED = "CANCELLED";
    public static final String OUTCOME_ALREADY_DONE = "SALE_NOT_PENDING";
    public static final String OUTCOME_NO_SALE = "NO_SALE";
    public static final String OUTCOME_PAID_AFTER_ALL = "PAYMENT_CAPTURED";

    /** Errors recorded on a request that waits or failed. */
    public static final String ERROR_SALE_NOT_FOUND = "SALE_NOT_FOUND_YET";
    public static final String ERROR_WALLET_UNAVAILABLE = "WALLET_UNAVAILABLE";
    public static final String ERROR_TENANT_MISSING = "TENANT_MISSING";
    public static final String ERROR_GAVE_UP = "GAVE_UP_AFTER_MAX_ATTEMPTS";
    public static final String ERROR_UNEXPECTED = "UNEXPECTED: ";
    /** A SETTLE waits: the wallet has no sale booked for the event yet (no attempt is counted). */
    public static final String ERROR_AWAITING_SALE = "AWAITING_SALE";
    /** A SETTLE given up: its sale was still not booked after {@code await-sale-max-age}. */
    public static final String ERROR_SALE_NEVER_BOOKED = "SALE_NEVER_BOOKED";
    /** The wallet's sale lookup could not answer (a failed attempt, retried with the usual backoff). */
    public static final String ERROR_LOOKUP_UNAVAILABLE = "SALE_LOOKUP_UNAVAILABLE";

    /** Most event ids asked of the wallet's sale lookup in one call (the wallet's own limit). */
    public static final int MAX_LOOKUP_EVENTS = 100;

    /** Reasons sent with a cancellation. */
    public static final String REASON_FAILED = "Gateway payment failed";
    public static final String REASON_EXPIRED = "Gateway payment expired";
    public static final String REASON_CANCELLED = "Gateway payment cancelled";

    /** Longest error or reason text stored. */
    public static final int MAX_TEXT = 500;
    /** The backoff stops doubling after this many attempts. */
    public static final int MAX_BACKOFF_DOUBLINGS = 16;
    /** MDC key and prefix of the correlation id of a settlement call. */
    public static final String CORRELATION_KEY = "correlationId";
    public static final String CORRELATION_PREFIX = "sale-settlement-";
}
