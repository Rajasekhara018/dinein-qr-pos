package com.heuristiq.notification.testsupport;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.OptimisticLockingFailureException;

import com.heuristiq.notification.domain.model.NotificationAttempt;
import com.heuristiq.notification.domain.repository.INotificationAttemptRepository;
import com.heuristiq.notification.domain.repository.PageQuery;
import com.heuristiq.notification.domain.repository.PageResult;

/** Test double with the same observable contract as the real adapters, including the optimistic version check. */
public class InMemoryAttemptRepository implements INotificationAttemptRepository {

    private final Map<String, NotificationAttempt> rows = new LinkedHashMap<>();
    /** When set, the next save throws it (once). */
    public RuntimeException failNextSave;
    /** When > 0, that many saves of existing rows fail with an optimistic-lock error first. */
    public int optimisticFailuresToInject;

    @Override
    public synchronized NotificationAttempt save(NotificationAttempt attempt) {
        if (failNextSave != null) {
            RuntimeException e = failNextSave;
            failNextSave = null;
            throw e;
        }
        NotificationAttempt stored = attempt.toBuilder().build();
        if (stored.getId() == null) {
            stored.setId(UUID.randomUUID().toString());
            stored.setVersion(0L);
        } else {
            NotificationAttempt current = rows.get(stored.getId());
            if (optimisticFailuresToInject > 0) {
                optimisticFailuresToInject--;
                throw new OptimisticLockingFailureException("injected");
            }
            if (current != null && !java.util.Objects.equals(current.getVersion(), stored.getVersion())) {
                throw new OptimisticLockingFailureException("stale version");
            }
            stored.setVersion(stored.getVersion() == null ? 0L : stored.getVersion() + 1);
        }
        rows.put(stored.getId(), stored);
        return stored.toBuilder().build();
    }

    @Override
    public synchronized Optional<NotificationAttempt> findById(String id) {
        return Optional.ofNullable(rows.get(id)).map(a -> a.toBuilder().build());
    }

    @Override
    public synchronized Optional<NotificationAttempt> findLatestByProviderMessageId(String providerMessageId) {
        return rows.values().stream()
                .filter(a -> providerMessageId.equals(a.getProviderMessageId()))
                .max(Comparator.comparing(NotificationAttempt::getStartedAt))
                .map(a -> a.toBuilder().build());
    }

    @Override
    public synchronized PageResult<NotificationAttempt> findByMessageId(String messageId, PageQuery pageQuery) {
        return page(rows.values().stream().filter(a -> messageId.equals(a.getMessageId())).toList(), pageQuery);
    }

    @Override
    public synchronized PageResult<NotificationAttempt> findByCorrelationId(String correlationId, PageQuery pageQuery) {
        return page(rows.values().stream().filter(a -> correlationId.equals(a.getCorrelationId())).toList(), pageQuery);
    }

    @Override
    public synchronized List<NotificationAttempt> findByLogId(String logId) {
        return rows.values().stream().filter(a -> logId.equals(a.getLogId()))
                .sorted(Comparator.comparingInt(NotificationAttempt::getAttemptNumber)).toList();
    }

    @Override
    public synchronized void purgeOlderThan(LocalDateTime cutoff) {
        rows.values().removeIf(a -> a.getStartedAt() != null && a.getStartedAt().isBefore(cutoff));
    }

    public synchronized List<NotificationAttempt> all() {
        return new ArrayList<>(rows.values());
    }

    private PageResult<NotificationAttempt> page(List<NotificationAttempt> matches, PageQuery q) {
        List<NotificationAttempt> sorted = matches.stream()
                .sorted(Comparator.comparing(NotificationAttempt::getStartedAt)
                        .thenComparingInt(NotificationAttempt::getAttemptNumber)).toList();
        int from = Math.min(q.page() * q.size(), sorted.size());
        int to = Math.min(from + q.size(), sorted.size());
        return new PageResult<>(sorted.subList(from, to), q.page(), q.size(), sorted.size());
    }
}
