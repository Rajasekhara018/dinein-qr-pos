package com.heuristiq.paymentservice.client.dto;

import com.heuristiq.paymentservice.shared.money.Money;
import java.math.BigDecimal;

/**
 * Subset of the Ride service's actual GET /rides/{rideId} response this service needs
 * before creating a payment (fare amount, the customer, and the driver to credit
 * earnings to). Field names/types match Rider_RideService's RideResponse record exactly
 * (id, customerId, driverId, estimatedFare, finalFare, status) so Jackson can deserialize
 * it — {@link #rideId()} and {@link #totalAmount()} below are convenience accessors kept
 * for the existing call sites in PaymentService/SplitFareService, not JSON properties.
 */
public record RideDetailsResponse(
        String id,
        String customerId,
        String driverId,
        Money estimatedFare,
        Money finalFare,
        String status
) {
    /** Alias for {@link #id()}, kept for call-site readability (this is a ride id). */
    public String rideId() {
        return id;
    }

    /** The amount to charge: the locked-in final fare once set, otherwise the estimate. */
    public Money totalAmount() {
        return finalFare != null ? finalFare : estimatedFare;
    }
}
