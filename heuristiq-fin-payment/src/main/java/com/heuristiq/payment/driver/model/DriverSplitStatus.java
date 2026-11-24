package com.heuristiq.payment.driver.model;

/**
 * Whether a driver payment record carries the ride's own fare split (decided once by the central commission plan and served by
 * Rider_RideService). This service never computes a commission of its own: until the split is known the fee and net stay empty.
 */
public enum DriverSplitStatus {
    /** Platform fee and net amount are the ride's stored split. */
    SPLIT,
    /**
     * The ride's split was not available yet (no plan in force, Ride unreachable or not answering): fee and net are empty and
     * stay so (history only: Payment no longer writes driver payment records).
     */
    PENDING_SPLIT,
    /**
     * Ride's split is for a different fare than the amount captured: fee and net stay empty and the record needs a person to
     * look at it (logged at ERROR); it is not retried, since the stored split will not change.
     */
    SPLIT_MISMATCH
}
