package com.heuristq.dinein.notification;

import com.heuristq.dinein.notification.dto.NotificationDtos.NotificationView;
import com.heuristq.dinein.notification.dto.NotificationDtos.UnreadCount;
import com.heuristq.dinein.shared.security.CurrentStaff;
import com.heuristq.dinein.shared.web.PageResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Inbox for the waiter screen: the same per-user inbox as the admin bell (a notification is visible when it targets
 * the caller's role or user id), reachable by waiters. New entries are announced on {@code /topic/waiter/notifications}.
 */
@RestController
@RequestMapping("/api/waiter")
public class WaiterNotificationController {

    private final InAppNotificationService inAppService;

    public WaiterNotificationController(InAppNotificationService inAppService) {
        this.inAppService = inAppService;
    }

    @GetMapping("/notifications")
    public PageResponse<NotificationView> list(@RequestParam(defaultValue = "false") boolean unreadOnly,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "20") int size) {
        return inAppService.list(CurrentStaff.require(), unreadOnly, page, size);
    }

    @GetMapping("/notifications/unread-count")
    public UnreadCount unreadCount() {
        return new UnreadCount(inAppService.unreadCount(CurrentStaff.require()));
    }

    @PostMapping("/notifications/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markRead(@PathVariable Long id) {
        inAppService.markRead(CurrentStaff.require(), id);
    }

    @PostMapping("/notifications/read-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markAllRead() {
        inAppService.markAllRead(CurrentStaff.require());
    }
}
