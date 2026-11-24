package com.heuristiq.notification.adapters.out.channel.inapp;

import com.heuristiq.notification.adapters.out.channel.ChannelAdapterConstants;
import com.heuristiq.notification.application.security.NotificationTenantContext;
import java.time.LocalDateTime;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.heuristiq.notification.domain.model.InAppNotification;
import com.heuristiq.notification.domain.repository.IInAppNotificationRepository;
import com.heuristiq.notification.application.port.out.ChannelProvider;
import com.heuristiq.notification.domain.model.NotificationChannel;

import lombok.RequiredArgsConstructor;

/**
 * IN_APP channel — single in-house implementation, no secondary provider SPI needed (unlike
 * EMAIL/SMS/WHATSAPP which pick between external vendors). Persists the notification, then
 * pushes it live to /user/{recipient}/queue/notifications if the recipient is connected.
 */
@Component
@RequiredArgsConstructor
public class InAppChannelProvider implements ChannelProvider {

    private static final Logger logger = LoggerFactory.getLogger(InAppChannelProvider.class);

    private final IInAppNotificationRepository repository;
    private final SimpMessagingTemplate messagingTemplate;

    @Override
    public NotificationChannel getChannel() {
        return NotificationChannel.IN_APP;
    }

    @Override
    public boolean send(String recipient, String subject, String body, String templateId) {
        if (!StringUtils.hasText(recipient)) {
            logger.warn("InAppChannelProvider: recipient (userId) blank, skipping");
            return false;
        }

        InAppNotification notification = new InAppNotification();
        // The organization of the notification being sent (set by the dispatch service around this call).
        notification.setTenantId(NotificationTenantContext.current());
        notification.setUserId(recipient);
        notification.setTitle(StringUtils.hasText(subject) ? subject : "Notification");
        notification.setMessage(body);
        notification.setRead(false);
        notification.setCreatedAt(LocalDateTime.now());

        InAppNotification saved = repository.save(notification);

        try {
            messagingTemplate.convertAndSendToUser(recipient, ChannelAdapterConstants.IN_APP_USER_DESTINATION, toPayload(saved));
        } catch (Exception e) {
            // DB write already succeeded — the client falls back to REST polling if the socket is down.
            logger.warn("InAppChannelProvider: WebSocket push failed for userId={}: {}", recipient, e.getMessage());
        }
        return true;
    }

    private Map<String, Object> toPayload(InAppNotification notification) {
        return Map.of(
                "id", notification.getId(),
                "userId", notification.getUserId(),
                "title", notification.getTitle(),
                "message", notification.getMessage(),
                "read", notification.isRead(),
                "createdAt", notification.getCreatedAt().toString());
    }
}
