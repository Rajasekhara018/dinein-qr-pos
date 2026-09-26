package com.heuristq.dinein.notification.email;

/**
 * Email provider SPI. To add a provider (SES, Mailgun, ...): implement this as a Spring bean with a unique
 * {@link #provider()} code and select it with {@code app.notifications.email.provider}.
 */
public interface EmailSender {

    /** Upper-case provider code, e.g. {@code SMTP}. */
    String provider();

    /** Sends synchronously; throws on failure (the dispatcher records it). */
    void send(EmailMessage message);
}
