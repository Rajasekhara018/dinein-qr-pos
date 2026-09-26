package com.heuristq.dinein.notification;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * {@code app.notifications.*}: per-channel on/off switch and provider. Provider credentials live in their own
 * records ({@code app.notifications.sms.twilio}, {@code .sms.msg91}, {@code .push.fcm}); SMTP uses {@code spring.mail.*}.
 */
@ConfigurationProperties(prefix = "app.notifications")
public record NotificationProperties(
        @DefaultValue Email email,
        @DefaultValue Sms sms,
        @DefaultValue Push push,
        @DefaultValue InApp inApp) {

    /** @param from sender address used by every email provider */
    public record Email(@DefaultValue("true") boolean enabled, @DefaultValue("LOG") String provider, String from) {
    }

    /** @param defaultCountryCode prefixed to 10-digit local numbers (phones are stored without it) */
    public record Sms(@DefaultValue("true") boolean enabled, @DefaultValue("LOG") String provider,
                      @DefaultValue("91") String defaultCountryCode) {
    }

    public record Push(@DefaultValue("true") boolean enabled, @DefaultValue("LOG") String provider) {
    }

    public record InApp(@DefaultValue("true") boolean enabled) {
    }
}
