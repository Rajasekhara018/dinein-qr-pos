package com.heuristiq.identity.centralaudit;

import com.heuristiq.identity.audit.AuditLog;
import com.heuristiq.identity.audit.AuditLogServiceUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

/**
 * UAM's audit entry point: hands each domain audit entry to the central audit service through the durable outbox
 * {@link CentralAuditPublisher}. UAM keeps no audit store of its own and offers no audit query. Never throws, so a
 * caller's business action is never failed by auditing.
 */
public class CentralAuditLogService implements AuditLogServiceUseCase {

    private static final Logger log = LoggerFactory.getLogger(CentralAuditLogService.class);

    private final CentralAuditPublisher publisher;
    private final Clock clock;

    public CentralAuditLogService(CentralAuditPublisher publisher, Clock clock) {
        this.publisher = publisher;
        this.clock = clock;
    }

    @Override
    public RecordOutput record(RecordInput input) {
        AuditLog entry = input.entry();
        try {
            Instant when = entry.getCreatedAt() == null ? clock.instant() : entry.getCreatedAt().toInstant(ZoneOffset.UTC);
            publisher.publish(CentralAuditEventMapper.toEvent(entry, when));
        } catch (RuntimeException e) {
            log.warn("uam.audit.publish-failed reason={}", e.toString());
        }
        return new RecordOutput(entry);
    }
}
