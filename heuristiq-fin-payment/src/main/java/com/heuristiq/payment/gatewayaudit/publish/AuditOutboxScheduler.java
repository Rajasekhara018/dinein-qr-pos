package com.heuristiq.paymentservice.gatewayaudit.publish;

import com.heuristiq.paymentservice.gatewayaudit.GatewayAuditConstants;
import lombok.RequiredArgsConstructor;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Scheduled entry points of the audit outbox; each run holds a ShedLock so only one instance drains. */
@Component
@RequiredArgsConstructor
public class AuditOutboxScheduler {

    private final AuditOutboxPublisher publisher;

    @Scheduled(fixedDelayString = "${rider.audit.publish.interval-ms:5000}")
    @SchedulerLock(name = GatewayAuditConstants.LOCK_DRAIN, lockAtMostFor = "PT2M", lockAtLeastFor = "PT0S")
    public void drain() {
        publisher.drain();
    }

    @Scheduled(fixedDelayString = "${rider.audit.publish.purge-interval-ms:3600000}", initialDelayString = "${rider.audit.publish.purge-interval-ms:3600000}")
    @SchedulerLock(name = GatewayAuditConstants.LOCK_PURGE, lockAtMostFor = "PT5M", lockAtLeastFor = "PT0S")
    public void purge() {
        publisher.purge();
    }
}
