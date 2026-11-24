package com.heuristiq.payment.payment.model;

/**
 * How a payment was made. CASH and UPI_DIRECT are direct collections: the rider paid the driver in cash or to the driver's own UPI
 * id, no gateway is involved and the record is written CAPTURED by Rider_RideService once the driver confirms it
 * (docs/DRIVER_FRANCHISE_SUBSCRIPTION_PLAN.md, D13). RAZORPAY means "through a gateway" (see {@link Payment#getGateway()}).
 */
public enum PaymentMethod {
    CASH,
    RAZORPAY,
    /** Paid to the driver's own UPI id (QR or {@code upi://pay} link), recorded by the ride service; never through a gateway. */
    UPI_DIRECT;

    /** True for a collection the driver took directly (cash or UPI to their own id): no gateway, no settlement from here. */
    public boolean isDirectCollection() {
        return this == CASH || this == UPI_DIRECT;
    }
}
