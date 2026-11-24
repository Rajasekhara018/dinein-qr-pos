package com.railcab.nms.adapters.in.scheduling;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.railcab.nms.application.port.in.AuditOutboxUseCase;

import lombok.RequiredArgsConstructor;

/**
 * Periodically hands queued communication audit events to the audit service. Safe on several NMS
 * instances: each outbox entry is leased before it is sent, and the audit service ignores a
 * repeated {@code eventId}. Not registered at all when {@code nms.audit.publish.enabled=false}.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "nms.audit.publish", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AuditOutboxScheduler {

    private final AuditOutboxUseCase auditOutbox;

    @Scheduled(fixedDelayString = "${nms.audit.publish.interval-ms:5000}", initialDelayString = "${nms.audit.publish.interval-ms:5000}")
    public void drain() {
        auditOutbox.drain();
    }
}
