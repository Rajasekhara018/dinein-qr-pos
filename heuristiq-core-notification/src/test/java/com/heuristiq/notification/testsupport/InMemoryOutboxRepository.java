package com.heuristiq.notification.testsupport;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.heuristiq.notification.domain.model.AuditOutboxEntry;
import com.heuristiq.notification.domain.model.AuditOutboxStatus;
import com.heuristiq.notification.domain.repository.IAuditOutboxRepository;

/** Test double with the same observable contract as the real outbox adapters (unique eventId, atomic lease). */
public class InMemoryOutboxRepository implements IAuditOutboxRepository {

    private final Map<String, AuditOutboxEntry> rows = new LinkedHashMap<>();
    /** When set, every insert throws it. */
    public RuntimeException failInserts;

    @Override
    public synchronized boolean insertIfAbsent(AuditOutboxEntry entry) {
        if (failInserts != null) {
            throw failInserts;
        }
        if (rows.values().stream().anyMatch(e -> e.getEventId().equals(entry.getEventId()))) {
            return false;
        }
        AuditOutboxEntry stored = entry.toBuilder().id(UUID.randomUUID().toString()).build();
        rows.put(stored.getId(), stored);
        return true;
    }

    @Override
    public synchronized List<AuditOutboxEntry> findDue(LocalDateTime now, int limit) {
        return rows.values().stream()
                .filter(e -> e.getStatus() == AuditOutboxStatus.PENDING && !e.getNextAttemptAt().isAfter(now)
                        && (e.getLockedUntil() == null || e.getLockedUntil().isBefore(now)))
                .sorted(Comparator.comparing(AuditOutboxEntry::getCreatedAt))
                .limit(limit).map(e -> e.toBuilder().build()).toList();
    }

    @Override
    public synchronized boolean tryLease(String id, String owner, LocalDateTime now, LocalDateTime leaseUntil) {
        AuditOutboxEntry e = rows.get(id);
        if (e == null || e.getStatus() != AuditOutboxStatus.PENDING
                || (e.getLockedUntil() != null && !e.getLockedUntil().isBefore(now))) {
            return false;
        }
        e.setLockedUntil(leaseUntil);
        e.setLockOwner(owner);
        return true;
    }

    @Override
    public synchronized void markSent(String id, LocalDateTime sentAt) {
        AuditOutboxEntry e = rows.get(id);
        e.setStatus(AuditOutboxStatus.SENT);
        e.setSentAt(sentAt);
        e.setLockedUntil(null);
        e.setLockOwner(null);
    }

    @Override
    public synchronized void markFailed(String id, int attempts, LocalDateTime nextAttemptAt, String error, boolean dead) {
        AuditOutboxEntry e = rows.get(id);
        e.setStatus(dead ? AuditOutboxStatus.DEAD : AuditOutboxStatus.PENDING);
        e.setAttempts(attempts);
        e.setNextAttemptAt(nextAttemptAt);
        e.setLastError(error);
        e.setLockedUntil(null);
        e.setLockOwner(null);
    }

    @Override
    public synchronized long countByStatus(AuditOutboxStatus status) {
        return rows.values().stream().filter(e -> e.getStatus() == status).count();
    }

    @Override
    public synchronized void purgeExpired(LocalDateTime now) {
        rows.values().removeIf(e -> e.getExpireAt() != null && e.getExpireAt().isBefore(now));
    }

    public synchronized List<AuditOutboxEntry> all() {
        return new ArrayList<>(rows.values());
    }
}
