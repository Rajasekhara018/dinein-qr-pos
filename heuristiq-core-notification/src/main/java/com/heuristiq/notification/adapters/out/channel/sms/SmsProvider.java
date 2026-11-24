package com.railcab.nms.adapters.out.channel.sms;

import com.railcab.nms.application.port.out.ProviderSendResult;

/**
 * SPI for SMS delivery providers.
 * Implementations: {@code TwilioSmsProvider}, {@code DigimileSmsProvider}.
 * Selected at runtime via {@code nms.provider.sms} (twilio | digimile).
 */
public interface SmsProvider {

    String getName();

    /**
     * @param to         E.164 phone number
     * @param body       message text
     * @param templateId DLT pre-approved template ID (may be null)
     */
    boolean send(String to, String body, String templateId);

    /**
     * Same send, also reporting the provider's message id (needed to match a delivery receipt) and
     * a failure reason. Providers that cannot say more keep this default.
     */
    default ProviderSendResult sendDetailed(String to, String body, String templateId) {
        return send(to, body, templateId)
                ? ProviderSendResult.ok(getName(), null, null)
                : ProviderSendResult.failed(getName(), ProviderSendResult.CODE_REJECTED, null);
    }
}
