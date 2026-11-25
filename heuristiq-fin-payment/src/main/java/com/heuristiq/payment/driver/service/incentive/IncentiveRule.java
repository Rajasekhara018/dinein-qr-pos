package com.heuristiq.payment.driver.service.incentive;
import java.math.BigDecimal;

/** Ported verbatim (shape) from Rider_BookingsAPI's {@code driver.incentive.IncentiveRule}. */
public interface IncentiveRule {

    String getRuleName();

    String getDescription();

    boolean isEligible(DriverIncentiveContext ctx);

    BigDecimal calculateBonus(DriverIncentiveContext ctx);
}
