package com.heuristiq.payment.payment.service;

import lombok.RequiredArgsConstructor;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodically resolves the outcome of PAYU/TOUCAN/PINELABS payments — the one real gap left after
 * the multi-gateway pass: {@code PaymentController} only has {@code POST /api/v1/payment/payments/webhook}
 * (RAZORPAY-specific, HMAC-verified). Nothing else ever calls the {@code StatusCheckGatewayClient}
 * adapters already built for the other 3 gateways, so a PAYU/TOUCAN/PINELABS checkout session had
 * no way to ever transition out of INITIATED, even after the customer actually paid.
 *
 * RAZORPAY is deliberately excluded from this poll (see
 * {@code PaymentRepository.findByStatusInAndGatewayIn}) — it stays exclusively webhook-driven, so
 * this scheduler can never race the webhook path for the same payment.
 *
 * Mirrors SettlementScheduler's fixed-rate + ShedLock pattern exactly (same reasoning: only one
 * instance of this service, if ever run with multiple replicas, should execute a given run).
 */
@Component
@RequiredArgsConstructor
public class PaymentStatusPollingScheduler {

    private final PaymentStatusPollingService paymentStatusPollingService;

    @Scheduled(fixedRateString = "${app.payment-polling.check-interval-ms:60000}")
    @SchedulerLock(name = "poll-gateway-payment-status", lockAtMostFor = "PT5M", lockAtLeastFor = "PT5S")
    public void run() {
        paymentStatusPollingService.pollPendingGatewayPayments();
    }
}
