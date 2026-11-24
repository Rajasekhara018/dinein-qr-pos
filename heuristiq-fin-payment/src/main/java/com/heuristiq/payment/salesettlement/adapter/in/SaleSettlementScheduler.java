package com.heuristiq.paymentservice.salesettlement.adapter.in;

import com.heuristiq.paymentservice.salesettlement.application.SaleSettlementService;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Sends due sale settle/cancel requests to Rider_WalletService. ShedLock keeps it to one instance at a time; every call is keyed,
 * so an overlap could not settle twice.
 */
@Component
public class SaleSettlementScheduler {

    private static final Logger log = LoggerFactory.getLogger(SaleSettlementScheduler.class);

    private final SaleSettlementService service;

    public SaleSettlementScheduler(SaleSettlementService service) {
        this.service = service;
    }

    @Scheduled(fixedDelayString = "${rider.sale-settlement.interval-ms:15000}")
    @SchedulerLock(name = "send-sale-settlements", lockAtMostFor = "PT5M", lockAtLeastFor = "PT2S")
    public void run() {
        try {
            service.sendDue();
        } catch (RuntimeException e) {
            log.error("Sale settlement run failed; requests stay queued", e);
        }
    }
}
