package com.heuristiq.documents.centralaudit;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodically hands queued audit events to the audit service. Safe on several DMS instances: each outbox entry is
 * leased before it is sent, and the audit service ignores a repeated {@code eventId}. Not registered when
 * {@code railcab.dms.audit.publish.enabled=false}.
 */
@Component
@ConditionalOnProperty(prefix = "railcab.dms.audit.publish", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AuditOutboxScheduler {

    private final AuditOutboxDrainer drainer;

    public AuditOutboxScheduler(AuditOutboxDrainer drainer) {
        this.drainer = drainer;
    }

    @Scheduled(fixedDelayString = "${railcab.dms.audit.publish.interval-ms:5000}",
            initialDelayString = "${railcab.dms.audit.publish.interval-ms:5000}")
    public void drain() {
        drainer.drain();
    }
}
