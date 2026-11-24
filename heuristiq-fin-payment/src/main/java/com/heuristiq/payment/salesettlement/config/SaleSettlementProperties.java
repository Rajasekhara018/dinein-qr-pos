package com.heuristiq.paymentservice.salesettlement.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * {@code rider.sale-settlement.*}: telling Rider_WalletService that a gateway sale's money arrived (or never will).
 *
 * @param enabled           false stores nothing and sends nothing (the wallet then holds gateway sales until someone settles them)
 * @param batchSize         most requests sent per run
 * @param initialBackoff    wait before the second try after a failure; doubles after each failure
 * @param maxBackoff        ceiling of the wait between two tries
 * @param maxAttempts       failed tries after which a request is given up (FAILED, logged at ERROR); the wallet's daily
 *                          STALE_SETTLEMENT alert still reports the sale. Waiting for the sale to be booked is not a failed try
 * @param awaitSaleInterval how often a settlement whose sale is not booked yet is looked up again (a sale is booked only after
 *                          its payment is complete, so the capture usually comes first); no attempt is counted while it waits
 * @param awaitSaleMaxAge   how long a settlement may wait for its sale before it is given up (FAILED, SALE_NEVER_BOOKED)
 */
@ConfigurationProperties(prefix = "rider.sale-settlement")
public record SaleSettlementProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("100") int batchSize,
        @DefaultValue("30s") Duration initialBackoff,
        @DefaultValue("1h") Duration maxBackoff,
        @DefaultValue("20") int maxAttempts,
        @DefaultValue("2m") Duration awaitSaleInterval,
        @DefaultValue("P7D") Duration awaitSaleMaxAge) {

    @ConstructorBinding
    public SaleSettlementProperties {
        if (batchSize < 1) {
            throw new IllegalArgumentException("rider.sale-settlement.batch-size must be at least 1");
        }
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("rider.sale-settlement.max-attempts must be at least 1");
        }
        if (initialBackoff.isNegative() || initialBackoff.isZero() || maxBackoff.compareTo(initialBackoff) < 0) {
            throw new IllegalArgumentException("rider.sale-settlement backoff must be positive and max-backoff >= initial-backoff");
        }
        if (awaitSaleInterval == null || awaitSaleInterval.isNegative() || awaitSaleInterval.isZero()
                || awaitSaleMaxAge == null || awaitSaleMaxAge.compareTo(awaitSaleInterval) < 0) {
            throw new IllegalArgumentException(
                    "rider.sale-settlement.await-sale-interval must be positive and await-sale-max-age >= await-sale-interval");
        }
    }

    /** Settings with the default sale wait (2 minutes between lookups, given up after 7 days). */
    public SaleSettlementProperties(boolean enabled, int batchSize, Duration initialBackoff, Duration maxBackoff, int maxAttempts) {
        this(enabled, batchSize, initialBackoff, maxBackoff, maxAttempts, Duration.ofMinutes(2), Duration.ofDays(7));
    }
}
