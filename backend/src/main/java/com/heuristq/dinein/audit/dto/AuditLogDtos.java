package com.heuristq.dinein.audit.dto;

import java.time.Instant;

public class AuditLogDtos {

    private AuditLogDtos() {
    }

    public record AuditLogEntry(Long id, Long staffUserId, String staffUsername, String action, String entityType,
                                Long entityId, String previousValue, String newValue, Instant createdAt) {
    }
}
