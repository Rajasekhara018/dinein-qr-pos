package com.heuristiq.paymentservice.config;

import com.heuristiq.paymentservice.payment.PaymentConstants;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * app.payment.fare-verification.* — whether POST /payments must charge exactly the fare Rider_RideService
 * holds for the ride (final fare once set, otherwise the estimate) instead of trusting the amount in the
 * request body.
 */
@Component
@ConfigurationProperties(prefix = "app.payment.fare-verification")
@Getter
@Setter
public class PaymentFareProperties {

    /** When true (default) a non-test payment whose amount differs from the ride fare is rejected with 400. */
    private boolean enabled = true;

    /** Allowed difference in rupees between the requested amount and the ride fare (rounding). */
    private BigDecimal toleranceRupees = PaymentConstants.FARE_TOLERANCE_RUPEES;
}
