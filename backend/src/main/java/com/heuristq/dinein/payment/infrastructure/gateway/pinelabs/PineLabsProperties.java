package com.heuristq.dinein.payment.infrastructure.gateway.pinelabs;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Pine Labs Online (Plural) credentials. Secrets come only from environment variables.
 *
 * @param merchantId       optional; when set, webhooks for another merchant id are ignored
 * @param webhookSecret    signing secret from the Pine Labs dashboard (base64, as shown there)
 * @param baseUrl          {@code https://pluraluat.v2.pinepg.in} (UAT) or {@code https://api.pluralpay.in} (live)
 * @param webhookTolerance maximum age/skew of a webhook's {@code webhook-timestamp}
 */
@ConfigurationProperties(prefix = "app.payments.pinelabs")
public record PineLabsProperties(
        String merchantId,
        String clientId,
        String clientSecret,
        String webhookSecret,
        @DefaultValue("https://pluraluat.v2.pinepg.in") String baseUrl,
        @DefaultValue("PT5M") Duration webhookTolerance) {

    public boolean isConfigured() {
        return notBlank(clientId) && notBlank(clientSecret);
    }

    /** Base URL without a trailing slash (an empty env var falls back to UAT). */
    public String apiBase() {
        String base = notBlank(baseUrl) ? baseUrl.trim() : "https://pluraluat.v2.pinepg.in";
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
