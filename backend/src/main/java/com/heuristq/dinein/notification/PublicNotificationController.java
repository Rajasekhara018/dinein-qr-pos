package com.heuristq.dinein.notification;

import com.heuristq.dinein.guest.CurrentGuest;
import com.heuristq.dinein.guest.GuestSession;
import com.heuristq.dinein.notification.dto.NotificationDtos.GuestPushSubscriptionRequest;
import com.heuristq.dinein.notification.dto.NotificationDtos.NotificationConfigResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public")
public class PublicNotificationController {

    private final PushSubscriptionService pushSubscriptions;

    public PublicNotificationController(PushSubscriptionService pushSubscriptions) {
        this.pushSubscriptions = pushSubscriptions;
    }

    /** Active push provider and its public browser config; {@code {pushEnabled:false}} when push is off or LOG. */
    @GetMapping("/notifications/config")
    public NotificationConfigResponse config() {
        return pushSubscriptions.clientConfig();
    }

    /** Registers the guest's device for "order ready" pushes; the order must belong to the guest session. */
    @PostMapping("/push-subscriptions")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void subscribe(@CurrentGuest GuestSession guest, @Valid @RequestBody GuestPushSubscriptionRequest request,
                          @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent) {
        String platform = request.platform() != null && !request.platform().isBlank() ? request.platform() : userAgent;
        pushSubscriptions.registerGuest(guest, request, platform);
    }
}
