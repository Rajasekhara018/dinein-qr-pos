package com.heuristiq.payment.wallet.service;

import lombok.RequiredArgsConstructor;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Credits paid top-ups whose wallet credit did not go through at once (wallet down, or refusing while purchases are
 * disabled). ShedLock keeps it to one instance at a time; each credit is idempotent, so an overlap could not double-credit.
 */
@Component
@RequiredArgsConstructor
public class WalletTopupCreditScheduler {

    private final WalletTopupCreditService creditService;

    @Scheduled(fixedRateString = "${rider.wallet.credit-retry-interval-ms:30000}")
    @SchedulerLock(name = "retry-wallet-topup-credit", lockAtMostFor = "PT5M", lockAtLeastFor = "PT5S")
    public void run() {
        creditService.retryDueCredits();
    }
}
