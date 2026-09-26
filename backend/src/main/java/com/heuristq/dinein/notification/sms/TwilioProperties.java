package com.heuristq.dinein.notification.sms;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Either {@code fromNumber} or {@code messagingServiceSid} identifies the sender. */
@ConfigurationProperties(prefix = "app.notifications.sms.twilio")
public record TwilioProperties(String accountSid, String authToken, String fromNumber, String messagingServiceSid,
                               @DefaultValue("https://api.twilio.com") String baseUrl) {

    public boolean isConfigured() {
        return notBlank(accountSid) && notBlank(authToken) && (notBlank(fromNumber) || notBlank(messagingServiceSid));
    }

    static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
