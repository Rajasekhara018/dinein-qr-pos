package com.heuristq.dinein.notification;

import com.heuristq.dinein.notification.dto.NotificationDtos.NotificationView;
import com.heuristq.dinein.notification.dto.NotificationDtos.StaffPushSubscriptionRequest;
import com.heuristq.dinein.notification.dto.NotificationDtos.UnreadCount;
import com.heuristq.dinein.shared.security.CurrentStaff;
import com.heuristq.dinein.shared.web.PageResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Inbox and push registration for the logged-in owner/manager. */
@RestController
@RequestMapping("/api/admin")
public class AdminNotificationController {

    private final InAppNotificationService inAppService;
    private final PushSubscriptionService pushSubscriptions;

    public AdminNotificationController(InAppNotificationService inAppService,
                                       PushSubscriptionService pushSubscriptions) {
        this.inAppService = inAppService;
        this.pushSubscriptions = pushSubscriptions;
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

    @PostMapping("/push-subscriptions")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void subscribe(@Valid @RequestBody StaffPushSubscriptionRequest request) {
        pushSubscriptions.registerStaff(CurrentStaff.require(), request);
    }

    @DeleteMapping("/push-subscriptions")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unsubscribe(@Valid @RequestBody StaffPushSubscriptionRequest request) {
        pushSubscriptions.unregisterStaff(CurrentStaff.require(), request);
    }
}
