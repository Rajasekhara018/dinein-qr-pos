package com.heuristiq.paymentservice.driver.service.incentive;

import java.time.LocalDate;

/**
 * Slimmed down from Rider_BookingsAPI's {@code DriverIncentiveContext}: that record also carried
 * {@code averageRating} and {@code tripsWithoutCancellation}, feeding the monolith's HIGH_RATING
 * and ZERO_CANCELLATION rules. This service has no source of truth for driver rating or
 * ride-cancellation history (those live in the Ride/Dispatch services), so only the TRIP_COUNT
 * rule — driven by real {@code driver_payments} rows this service already owns — is ported. See
 * README's "Deliberately deferred" section.
 */
public record DriverIncentiveContext(
        String driverId,
        int tripsThisWeek,
        LocalDate weekStartDate
) {
}
