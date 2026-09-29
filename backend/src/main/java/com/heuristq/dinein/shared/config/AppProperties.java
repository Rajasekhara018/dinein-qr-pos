package com.heuristq.dinein.shared.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.List;

@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        @NotBlank String publicBaseUrl,
        Cors cors,
        Cookies cookies,
        Security security,
        Orders orders,
        Images images,
        @DefaultValue Privacy privacy,
        @DefaultValue Notifications notifications,
        @DefaultValue Seed seed) {

    public record Cors(List<String> allowedOrigins) {
    }

    public record Cookies(@DefaultValue("true") boolean secure) {
    }

    public record Security(
            @NotBlank String jwtSecret,
            Duration accessTokenTtl,
            Duration refreshTokenTtl,
            Duration deviceTokenTtl,
            @NotBlank String guestSessionSecret,
            Duration guestSessionTtl,
            int maxFailedLogins,
            Duration lockoutDuration,
            String bootstrapOwnerUsername,
            String bootstrapOwnerPassword,
            String platformAdminKey) {
    }

    public record Orders(Duration paymentTimeout, long expiryCheckIntervalMs) {
    }

    public record Images(String orphanCleanupCron, Duration orphanGrace) {
    }

    public record Seed(@DefaultValue("false") boolean sampleData) {
    }

    /** GDPR data minimisation: how long a finished order keeps its guest-identifying fields. */
    public record Privacy(@DefaultValue("90") int guestDataRetentionDays,
                          @DefaultValue("0 15 3 * * *") String guestDataRedactionCron) {
    }

    public record Notifications(@DefaultValue("0 0 7 * * *") String dailySummaryCron,
                                @DefaultValue("0 */5 * * * *") String deviceOfflineCheckCron,
                                @DefaultValue("PT10M") Duration deviceOfflineThreshold) {
    }
}
