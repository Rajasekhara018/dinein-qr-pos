package com.railcab.nms.adapters.out.channel.whatsapp;

import com.railcab.nms.application.port.out.ProviderSendResult;

/**
 * SPI for WhatsApp delivery providers.
 * Implementations: {@code TwilioWhatsAppProvider}, {@code MetaWhatsAppProvider}, {@code DigimileWhatsAppProvider}.
 * Selected at runtime via {@code nms.provider.whatsapp} (twilio | meta | digimile).
 */
public interface WhatsAppProvider {

    String getName();

    boolean send(String to, String body);

    /** Same send, also reporting the provider's message id and a failure reason; default wraps {@link #send}. */
    default ProviderSendResult sendDetailed(String to, String body) {
        return send(to, body)
                ? ProviderSendResult.ok(getName(), null, null)
                : ProviderSendResult.failed(getName(), ProviderSendResult.CODE_REJECTED, null);
    }
}
