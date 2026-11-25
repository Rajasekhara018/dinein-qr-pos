package com.heuristiq.notification.application.usecase.support;

import java.util.List;
import java.util.Map;

import com.heuristiq.notification.application.dto.NotificationRequest;
import com.heuristiq.notification.application.dto.UamUserSummary;
import com.heuristiq.notification.domain.model.NotificationChannel;

/**
 * Fluent builder for synthesizing a {@link NotificationRequest} from a broadcast fan-out.
 *
 * <p>Unlike the REST/Kafka entry points -- which receive an already-populated
 * {@code NotificationRequest} from the caller -- broadcast dispatch has to assemble one
 * field-by-field per recipient, including channel-conditional recipient targeting
 * (only set {@code toEmail} if EMAIL is requested, only set {@code toPhone} if SMS/WHATSAPP
 * is requested, etc.). Centralizing that staged, conditional assembly here keeps
 * {@link com.heuristiq.notification.application.usecase.BroadcastDispatchScheduler} focused on
 * orchestration rather than field-by-field DTO plumbing.
 */
public final class NotificationRequestBuilder {

    private final NotificationRequest request = new NotificationRequest();

    private NotificationRequestBuilder() {
    }

    public static NotificationRequestBuilder newRequest() {
        return new NotificationRequestBuilder();
    }

    public NotificationRequestBuilder messageId(String messageId) {
        request.setMessageId(messageId);
        return this;
    }

    public NotificationRequestBuilder correlationId(String correlationId) {
        request.setCorrelationId(correlationId);
        return this;
    }

    /** Origin of the request (all optional): who asked, and which entity the message is about. */
    public NotificationRequestBuilder origin(String sourceService, String triggeringUserId, String entityType, String entityId) {
        request.setSourceService(sourceService);
        request.setTriggeringUserId(triggeringUserId);
        request.setEntityType(entityType);
        request.setEntityId(entityId);
        return this;
    }

    /** Organization the notification is sent on behalf of (a broadcast's own); null when it has none. */
    public NotificationRequestBuilder tenantId(String tenantId) {
        request.setTenantId(tenantId);
        return this;
    }

    public NotificationRequestBuilder event(String event) {
        request.setEvent(event);
        return this;
    }

    public NotificationRequestBuilder variables(Map<String, String> variables) {
        request.setVariables(variables);
        return this;
    }

    public NotificationRequestBuilder channels(List<NotificationChannel> channels) {
        request.setChannels(channels);
        return this;
    }

    /**
     * Populates the channel-specific recipient fields (toEmail/toPhone/userIds) for the given
     * channel set and recipient -- mirroring exactly which field each channel type reads,
     * per the {@link NotificationRequest} class javadoc.
     */
    public NotificationRequestBuilder recipientsFor(List<NotificationChannel> channels, UamUserSummary recipient) {
        if (channels.contains(NotificationChannel.EMAIL) && recipient.email() != null) {
            request.setToEmail(List.of(recipient.email()));
        }
        if ((channels.contains(NotificationChannel.SMS) || channels.contains(NotificationChannel.WHATSAPP))
                && recipient.phoneNumber() != null && !recipient.phoneNumber().e164().isEmpty()) {
            request.setToPhone(recipient.phoneNumber().e164());
        }
        if (channels.contains(NotificationChannel.IN_APP) || channels.contains(NotificationChannel.FCM_PUSH)) {
            request.setUserIds(List.of(recipient.id()));
        }
        return this;
    }

    public NotificationRequest build() {
        return request;
    }
}
