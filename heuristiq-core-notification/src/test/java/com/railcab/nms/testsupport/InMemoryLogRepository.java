package com.railcab.nms.testsupport;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.railcab.nms.domain.model.NotificationChannel;
import com.railcab.nms.domain.model.NotificationLog;
import com.railcab.nms.domain.repository.INotificationLogRepository;
import com.railcab.nms.domain.repository.PageQuery;
import com.railcab.nms.domain.repository.PageResult;

/** Minimal test double: stores rows and assigns ids; the list/count queries are not needed by these tests. */
public class InMemoryLogRepository implements INotificationLogRepository {

    private final Map<String, NotificationLog> rows = new LinkedHashMap<>();

    @Override
    public synchronized NotificationLog save(NotificationLog log) {
        NotificationLog stored = log.toBuilder().build();
        if (stored.getId() == null) {
            stored.setId(UUID.randomUUID().toString());
        }
        rows.put(stored.getId(), stored);
        return stored.toBuilder().build();
    }

    @Override
    public synchronized Optional<NotificationLog> findById(String id) {
        return Optional.ofNullable(rows.get(id)).map(l -> l.toBuilder().build());
    }

    public synchronized List<NotificationLog> all() {
        return List.copyOf(rows.values());
    }

    @Override
    public PageResult<NotificationLog> findAll(PageQuery pageQuery) {
        throw new UnsupportedOperationException();
    }

    @Override
    public PageResult<NotificationLog> findByEvent(String event, PageQuery pageQuery) {
        throw new UnsupportedOperationException();
    }

    @Override
    public PageResult<NotificationLog> findByCorrelationId(String correlationId, PageQuery pageQuery) {
        throw new UnsupportedOperationException();
    }

    @Override
    public PageResult<NotificationLog> findByChannel(NotificationChannel channel, PageQuery pageQuery) {
        throw new UnsupportedOperationException();
    }

    @Override
    public PageResult<NotificationLog> findBySentAtBetween(LocalDateTime from, LocalDateTime to, PageQuery pageQuery) {
        throw new UnsupportedOperationException();
    }

    @Override
    public long countByChannelAndSuccessTrue(NotificationChannel channel) {
        return rows.values().stream().filter(l -> l.getChannel() == channel && l.isSuccess()).count();
    }

    @Override
    public long countByChannelAndSuccessFalse(NotificationChannel channel) {
        return rows.values().stream().filter(l -> l.getChannel() == channel && !l.isSuccess()).count();
    }

    @Override
    public long countByChannelAndSuccess(NotificationChannel channel, boolean success,
            com.railcab.nms.domain.repository.TenantScope scope) {
        return rows.values().stream()
                .filter(l -> l.getChannel() == channel && l.isSuccess() == success && scope.allows(l.getTenantId()))
                .count();
    }

    @Override
    public void purgeOlderThan(LocalDateTime cutoff) {
        rows.values().removeIf(l -> l.getSentAt() != null && l.getSentAt().isBefore(cutoff));
    }
}
