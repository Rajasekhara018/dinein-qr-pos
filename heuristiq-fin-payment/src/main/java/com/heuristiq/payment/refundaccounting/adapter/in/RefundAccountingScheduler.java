package com.heuristiq.paymentservice.refundaccounting.adapter.in;

import com.heuristiq.paymentservice.refundaccounting.application.RefundAccountingService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Delivery may pause independently; recording verified refunds in the durable queue never pauses. */
@Component
@ConditionalOnProperty(name = "rider.refund-accounting.scheduler-enabled", havingValue = "true", matchIfMissing = true)
public class RefundAccountingScheduler {
    private final RefundAccountingService accounting;
    public RefundAccountingScheduler(RefundAccountingService accounting) { this.accounting = accounting; }

    @Scheduled(fixedDelayString = "${rider.refund-accounting.poll-ms:10000}")
    public void deliverDue() { accounting.deliverDue(); }
}
