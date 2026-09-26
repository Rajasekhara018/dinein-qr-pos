package com.heuristq.dinein.notification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.Map;

public final class NotificationDtos {

    private NotificationDtos() {
    }

    public record NotificationView(Long id, String event, String title, String body, String link, String severity,
                                   Long orderId, boolean read, Instant createdAt) {
    }

    public record UnreadCount(long count) {
    }

    /**
     * Message on {@code /topic/staff/notifications}. The topic is shared by owners and managers, so clients show it
     * only when {@code audience/recipient} matches them (or simply refetch the unread count).
     */
    public record StaffNotificationMessage(String type, String audience, String recipient,
                                           NotificationView notification) {
    }

    public record StaffPushSubscriptionRequest(
            @NotBlank @Size(max = 20) String provider,
            @NotBlank @Size(max = 1024) String token,
            @Size(max = 120) String platform) {
    }

    public record GuestPushSubscriptionRequest(
            @NotBlank @Size(max = 20) String provider,
            @NotBlank @Size(max = 1024) String token,
            @NotNull Long orderId,
            @Size(max = 120) String platform) {
    }

    /** {@code client} holds the push provider's public browser config (e.g. FCM {@code vapidKey}, {@code firebaseConfig}). */
    public record NotificationConfigResponse(boolean pushEnabled, String provider, Map<String, Object> client) {
    }
}
