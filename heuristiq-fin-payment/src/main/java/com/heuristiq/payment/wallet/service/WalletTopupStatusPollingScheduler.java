package com.heuristiq.paymentservice.wallet.service;

import lombok.RequiredArgsConstructor;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Mirrors PaymentStatusPollingScheduler exactly, for wallet top-ups instead of ride payments. */
@Component
@RequiredArgsConstructor
public class WalletTopupStatusPollingScheduler {
    private final WalletTopupService walletTopupService;

    @Scheduled(fixedRateString = "${app.payment-polling.check-interval-ms:60000}")
    @SchedulerLock(name = "poll-wallet-topup-status", lockAtMostFor = "PT5M", lockAtLeastFor = "PT5S")
    public void run() {
        walletTopupService.pollPendingTopups();
    }
}
