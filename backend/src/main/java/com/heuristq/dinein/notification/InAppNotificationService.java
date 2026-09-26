package com.heuristq.dinein.notification;

import com.heuristq.dinein.notification.NotificationTemplates.Message;
import com.heuristq.dinein.notification.domain.InAppAudience;
import com.heuristq.dinein.notification.domain.InAppNotificationEntity;
import com.heuristq.dinein.notification.domain.InAppNotificationRepository;
import com.heuristq.dinein.notification.dto.NotificationDtos.NotificationView;
import com.heuristq.dinein.notification.dto.NotificationDtos.StaffNotificationMessage;
import com.heuristq.dinein.realtime.RealtimePublisher;
import com.heuristq.dinein.shared.exception.ApiException;
import com.heuristq.dinein.shared.security.StaffPrincipal;
import com.heuristq.dinein.shared.web.PageResponse;
import com.heuristq.dinein.staff.domain.StaffRole;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Staff inbox. Rows target a role or a single user; read state is kept per user in {@code notification_read} so a
 * role-wide notification read by one owner stays unread for the others.
 */
@Slf4j
@Service
public class InAppNotificationService {

    private static final int MAX_PAGE_SIZE = 100;

    private final InAppNotificationRepository repository;
    private final RealtimePublisher realtimePublisher;

    public InAppNotificationService(InAppNotificationRepository repository, RealtimePublisher realtimePublisher) {
        this.repository = repository;
        this.realtimePublisher = realtimePublisher;
    }

    /**
     * Stores the notification (committed immediately) and then announces it: WAITER notifications on
     * {@code /topic/waiter/notifications}, everything else on {@code /topic/staff/notifications}.
     */
    public InAppNotificationEntity createForRole(StaffRole role, NotificationEvent event, Message message, Long orderId) {
        InAppNotificationEntity n = new InAppNotificationEntity();
        n.setAudience(InAppAudience.STAFF_ROLE);
        n.setRecipient(role.name());
        n.setEvent(event);
        n.setTitle(cap(message.subject(), 120));
        n.setBody(cap(message.body(), 500));
        n.setLink(cap(message.link(), 300));
        n.setSeverity(event.severity());
        n.setRelatedOrderId(orderId);
        repository.save(n);
        StaffNotificationMessage announcement = new StaffNotificationMessage("NOTIFICATION", n.getAudience().name(),
                n.getRecipient(), view(n, false));
        if (role == StaffRole.WAITER) {
            realtimePublisher.toWaiterNotifications(announcement);
        } else {
            realtimePublisher.toStaffNotifications(announcement);
        }
        return n;
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationView> list(StaffPrincipal staff, boolean unreadOnly, int page, int size) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
        Page<InAppNotificationEntity> result = repository.findVisible(staff.role().name(), userKey(staff),
                staff.userId(), unreadOnly, pageable);
        List<Long> ids = result.getContent().stream().map(InAppNotificationEntity::getId).toList();
        Set<Long> read = ids.isEmpty() ? Set.of() : new HashSet<>(repository.findReadIds(staff.userId(), ids));
        return PageResponse.of(result, n -> view(n, read.contains(n.getId())));
    }

    @Transactional(readOnly = true)
    public long unreadCount(StaffPrincipal staff) {
        return repository.countUnread(staff.role().name(), userKey(staff), staff.userId());
    }

    @Transactional
    public void markRead(StaffPrincipal staff, Long id) {
        if (!repository.isVisible(id, staff.role().name(), userKey(staff))) {
            throw ApiException.notFound("Notification");
        }
        repository.markRead(id, staff.userId());
    }

    @Transactional
    public int markAllRead(StaffPrincipal staff) {
        int marked = repository.markAllRead(staff.role().name(), userKey(staff), staff.userId());
        log.info("notification.read_all userId={} marked={}", staff.userId(), marked);
        return marked;
    }

    private static NotificationView view(InAppNotificationEntity n, boolean read) {
        return new NotificationView(n.getId(), n.getEvent().name(), n.getTitle(), n.getBody(), n.getLink(),
                n.getSeverity().name(), n.getRelatedOrderId(), read, n.getCreatedAt());
    }

    private static String userKey(StaffPrincipal staff) {
        return String.valueOf(staff.userId());
    }

    private static String cap(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() > max ? value.substring(0, max) : value;
    }
}
