package com.heuristiq.notification.adapters.out.channel.voice;

import java.util.Map;

import com.heuristiq.notification.application.port.out.ProviderSendResult;

/**
 * SPI for automated voice-call (IVR) providers. Implementations: {@code ExotelVoiceProvider}.
 * Selected at runtime via {@code nms.provider.voice} (exotel | none).
 */
public interface VoiceProvider {

    /** Selection key, matched case-insensitively against {@code nms.provider.voice}. */
    String getName();

    /**
     * Places one call.
     *
     * @param toPhone phone number to call
     * @param script  rendered template text (what the call is about); the spoken prompt lives in the provider's flow
     * @param data    message metadata (entityType, entityId, eventCode, type, messageId); may be empty, never null
     * @return the provider's call id as {@code providerMessageId} on success, so the status callback can be matched
     */
    ProviderSendResult call(String toPhone, String script, Map<String, String> data);
}
