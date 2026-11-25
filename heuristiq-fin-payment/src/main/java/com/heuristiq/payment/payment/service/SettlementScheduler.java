package com.heuristiq.payment.payment.service;

import lombok.RequiredArgsConstructor;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodically bookkeeps the CAPTURED -> SETTLED payment-state transition (HLD §17.2), which
 * previously had no trigger anywhere in this service. This is an internal bookkeeping transition
 * only — a real payment gateway's settlement cycle eventually reconciles captured funds into the
 * merchant's bank account on its own schedule (T+1/T+2 for Razorpay); there is no "settlement API"
 * to call here, so this job simply marks payments old enough that they'd realistically have
 * settled by now, matching how the monolith and this service's own README already described this
 * gap ("CAPTURED/SETTLED" states exist, only the trigger was missing).
 *
 * Mirrors Rider_RideService's StaleSearchScheduler/SosEscalationScheduler pattern: fixed-rate
 * {@code @Scheduled} + ShedLock's {@code @SchedulerLock} so only one instance of this service (if
 * ever run with multiple replicas) executes a given run.
 */
@Component
@RequiredArgsConstructor
public class SettlementScheduler {

    private final PaymentSettlementService paymentSettlementService;

    @Scheduled(fixedRateString = "${app.settlement.check-interval-ms:3600000}")
    @SchedulerLock(name = "settle-captured-payments", lockAtMostFor = "PT10M", lockAtLeastFor = "PT5S")
    public void run() {
        paymentSettlementService.settleOldCapturedPayments();
    }
}
