package com.heuristiq.paymentservice.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * app.payment-polling.check-interval-ms / max-age-hours — drives PaymentStatusPollingScheduler,
 * which is how PAYU/TOUCAN/PINELABS payments ever learn their outcome (those 3 gateways have no
 * webhook ingestion in this service) and the fallback for RAZORPAY when its webhook does not arrive.
 */
@Component
@ConfigurationProperties(prefix = "app.payment-polling")
@Getter
@Setter
public class PaymentPollingProperties {
    private long checkIntervalMs = 60000;
    private int maxAgeHours = 24;
    /**
     * RAZORPAY payments are normally resolved by its webhook; the poll only steps in for ones older
     * than this, so it does not race the webhook for a payment that has just been made.
     */
    private int razorpayGraceSeconds = 120;
    /**
     * Candidates read per page. Each run walks every candidate page by page (keyset on the payment
     * id), so the run never holds an unbounded list and never one transaction across the batch.
     */
    private int pageSize = 100;
    /**
     * Gateway status checks in flight at once, on virtual threads. Kept small: each check that
     * resolves a payment then takes a pooled database connection for its own short transaction.
     */
    private int concurrency = 4;
    /**
     * A run stops reading new pages once it has used this much time; the rest are polled by the next run.
     * Kept below the scheduler's ShedLock lockAtMostFor (5 minutes) so a second instance never starts a
     * run over the same payments while this one is still going.
     */
    private Duration runBudget = Duration.ofMinutes(4);
}
