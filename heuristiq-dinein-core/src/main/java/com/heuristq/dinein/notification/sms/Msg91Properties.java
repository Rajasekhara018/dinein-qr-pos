package com.heuristq.dinein.notification.sms;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.Map;

/**
 * @param templates MSG91 flow template id per event, keyed by the event name in kebab case
 *                  ({@code order-ready}, {@code order-cancelled}); Indian DLT rules require pre-approved templates
 */
@ConfigurationProperties(prefix = "app.notifications.sms.msg91")
public record Msg91Properties(String authKey, @DefaultValue("https://control.msg91.com") String baseUrl,
                              Map<String, String> templates) {

    public boolean isConfigured() {
        return authKey != null && !authKey.isBlank();
    }
}
