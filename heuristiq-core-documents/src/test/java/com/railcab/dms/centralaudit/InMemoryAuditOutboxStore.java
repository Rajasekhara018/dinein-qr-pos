package com.railcab.dms.centralaudit;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Fake outbox with the observable behaviour of the real adapters: unique event id, atomic lease, due ordering. */
public class InMemoryAuditOutboxStore implements AuditOutboxStore {

    public final Map<String, OutboxEntry> rows = new LinkedHashMap<>();
    public boolean failInserts;
    public int purgeCalls;

    @Override
    public synchronized boolean insertIfAbsent(OutboxEntry entry) {
        if (failInserts) {
            throw new IllegalStateException("database down");
        }
        if (rows.values().stream().anyMatch(e -> e.eventId().equals(entry.eventId()))) {
            return false;
        }
        String id = UUID.randomUUID().toString();
        rows.put(id, new OutboxEntry(id, entry.eventId(), entry.payload(), entry.status(), entry.attempts(),
                entry.nextAttemptAt(), entry.lockedUntil(), entry.lockOwner(), entry.lastError(), entry.createdAt(),
                entry.sentAt(), entry.expireAt()));
        return true;
    }

    @Override
    public synchronized List<OutboxEntry> findDue(Instant now, int limit) {
        List<OutboxEntry> due = new ArrayList<>();
        for (OutboxEntry e : rows.values()) {
            if (e.status() == OutboxStatus.PENDING && !e.nextAttemptAt().isAfter(now)
                    && (e.lockedUntil() == null || e.lockedUntil().isBefore(now))) {
                due.add(e);
            }
        }
        due.sort(Comparator.comparing(OutboxEntry::createdAt));
        return due.size() > limit ? new ArrayList<>(due.subList(0, limit)) : due;
    }

    @Override
    public synchronized boolean tryLease(String id, String owner, Instant now, Instant leaseUntil) {
        OutboxEntry e = rows.get(id);
        if (e == null || e.status() != OutboxStatus.PENDING || (e.lockedUntil() != null && !e.lockedUntil().isBefore(now))) {
            return false;
        }
        rows.put(id, new OutboxEntry(id, e.eventId(), e.payload(), e.status(), e.attempts(), e.nextAttemptAt(), leaseUntil,
                owner, e.lastError(), e.createdAt(), e.sentAt(), e.expireAt()));
        return true;
    }

    @Override
    public synchronized void markSent(String id, Instant sentAt) {
        OutboxEntry e = rows.get(id);
        rows.put(id, new OutboxEntry(id, e.eventId(), e.payload(), OutboxStatus.SENT, e.attempts(), e.nextAttemptAt(), null,
                null, null, e.createdAt(), sentAt, e.expireAt()));
    }

    @Override
    public synchronized void markFailed(String id, int attempts, Instant nextAttemptAt, String error, boolean dead) {
        OutboxEntry e = rows.get(id);
        rows.put(id, new OutboxEntry(id, e.eventId(), e.payload(), dead ? OutboxStatus.DEAD : OutboxStatus.PENDING, attempts,
                nextAttemptAt, null, null, error, e.createdAt(), e.sentAt(), e.expireAt()));
    }

    @Override
    public synchronized long countByStatus(OutboxStatus status) {
        return rows.values().stream().filter(e -> e.status() == status).count();
    }

    @Override
    public synchronized void purgeExpired(Instant now) {
        purgeCalls++;
        rows.values().removeIf(e -> e.expireAt() != null && e.expireAt().isBefore(now));
    }

    public synchronized List<OutboxEntry> all() {
        return new ArrayList<>(rows.values());
    }
}
