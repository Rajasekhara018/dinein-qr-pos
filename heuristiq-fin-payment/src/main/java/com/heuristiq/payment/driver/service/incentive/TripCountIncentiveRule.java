package com.heuristiq.payment.driver.service.incentive;

import org.springframework.stereotype.Component;
import java.math.BigDecimal;

/**
 * Ported verbatim from Rider_BookingsAPI's {@code driver.incentive.TripCountIncentiveRule} —
 * same thresholds/bonuses (10/20/30 trips -> 100/250/500), same rule name/description. Only
 * the trip-count source differs: the monolith reads a Redis weekly counter
 * ({@code driver:trips:week:<id>}); this service has no such counter, so
 * {@code DriverIncentiveService} counts real {@code driver_payments} rows (one per completed,
 * paid-for ride) created since the current week's Monday — an equivalent real signal, not an
 * invented one.
 */
@Component
public class TripCountIncentiveRule implements IncentiveRule {

    @Override
    public String getRuleName() {
        return "TRIP_COUNT";
    }

    @Override
    public String getDescription() {
        return "Bonus for completing 10/20/30 trips per week";
    }

    @Override
    public boolean isEligible(DriverIncentiveContext ctx) {
        return ctx.tripsThisWeek() >= 10;
    }

    @Override
    public BigDecimal calculateBonus(DriverIncentiveContext ctx) {
        int trips = ctx.tripsThisWeek();
        if (trips >= 30) {
            return new BigDecimal("500.00");
        } else if (trips >= 20) {
            return new BigDecimal("250.00");
        } else if (trips >= 10) {
            return new BigDecimal("100.00");
        }
        return BigDecimal.ZERO;
    }
}
