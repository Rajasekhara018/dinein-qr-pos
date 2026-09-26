package com.heuristq.dinein.notification.sms;

/**
 * SMS provider SPI. To add a provider: implement this as a Spring bean with a unique {@link #provider()} code and
 * select it with {@code app.notifications.sms.provider}.
 */
public interface SmsSender {

    /** Upper-case provider code, e.g. {@code TWILIO}. */
    String provider();

    /** Sends synchronously; throws on failure (the dispatcher records it). */
    void send(SmsMessage message);
}
