package com.railcab.nms.application.port.out;

import java.util.Map;

import com.railcab.nms.domain.model.NotificationChannel;

/**
 * Shared contract for all notification channel providers.
 *
 * @param recipient  channel-appropriate destination (email address, phone number, FCM token…)
 * @param subject    only used by EMAIL; ignored by other channels
 * @param body       rendered message body
 * @param templateId optional pre-approved external template ID (SMS DLT / WhatsApp approved template)
 * @return true on success
 */
public interface ChannelProvider {

    NotificationChannel getChannel();

    boolean send(String recipient, String subject, String body, String templateId);

    /**
     * Same send, but reporting the provider's own message id and a failure code/reason so each
     * attempt can be recorded and later matched to a delivery receipt. Providers that cannot say
     * more keep this default, which wraps {@link #send}.
     */
    default ProviderSendResult sendDetailed(String recipient, String subject, String body, String templateId) {
        return send(recipient, subject, body, templateId)
                ? ProviderSendResult.ok(null, null, null)
                : ProviderSendResult.failed(null, ProviderSendResult.CODE_REJECTED, null);
    }

    /**
     * Same detailed send, also carrying structured data about the message (the push data contract: type, eventCode,
     * entityType, entityId, rideId, url and the non-sensitive variables; for VOICE also the message id). Channels that
     * cannot carry data keep this default, which ignores {@code data} and calls the four-argument form.
     *
     * @param data structured metadata; may be null or empty
     */
    default ProviderSendResult sendDetailed(String recipient, String subject, String body, String templateId,
            Map<String, String> data) {
        return sendDetailed(recipient, subject, body, templateId);
    }
}
