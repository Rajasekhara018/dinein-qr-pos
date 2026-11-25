package com.heuristiq.notification.adapters.out.channel.email;

import com.heuristiq.notification.application.port.out.ProviderSendResult;

/**
 * SPI for email delivery providers.
 * Implementations: {@code SmtpEmailProvider}, {@code MailgunEmailProvider}, {@code AwsSesEmailProvider}.
 * Selected at runtime via {@code nms.provider.email} (smtp | mailgun | ses).
 */
public interface EmailProvider {

    /** Identifier that matches the {@code nms.provider.email} property value */
    String getName();

    boolean send(String to, String subject, String htmlBody);

    /** Same send, also reporting the provider's message id and a failure reason; default wraps {@link #send}. */
    default ProviderSendResult sendDetailed(String to, String subject, String htmlBody) {
        return send(to, subject, htmlBody)
                ? ProviderSendResult.ok(getName(), null, null)
                : ProviderSendResult.failed(getName(), ProviderSendResult.CODE_REJECTED, null);
    }
}
