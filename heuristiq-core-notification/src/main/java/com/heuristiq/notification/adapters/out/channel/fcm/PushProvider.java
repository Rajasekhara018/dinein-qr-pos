package com.heuristiq.notification.adapters.out.channel.fcm;

import java.util.Map;

import com.heuristiq.notification.application.port.out.ProviderSendResult;

/**
 * SPI for mobile push delivery providers.
 * Implementations: {@code FirebasePushProvider}.
 * Selected at runtime via {@code nms.provider.push} (fcm).
 */
public interface PushProvider {

    String getName();

    /**
     * @param token FCM / APNs device registration token
     * @param title notification title
     * @param body  notification body text
     */
    boolean send(String token, String title, String body);

    /** Same send, also reporting the provider's message id and a failure reason; default wraps {@link #send}. */
    default ProviderSendResult sendDetailed(String token, String title, String body) {
        return send(token, title, body)
                ? ProviderSendResult.ok(getName(), null, null)
                : ProviderSendResult.failed(getName(), ProviderSendResult.CODE_REJECTED, null);
    }

    /**
     * Same detailed send with a data map (push data contract). Providers that cannot carry data keep this default,
     * which drops {@code data}.
     */
    default ProviderSendResult sendDetailed(String token, String title, String body, Map<String, String> data) {
        return sendDetailed(token, title, body);
    }
}
