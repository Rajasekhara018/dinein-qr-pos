package com.railcab.dms.expiry.config;

import java.time.ZoneId;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

/**
 * Document expiry reminders and the lapse check (D12), {@code railcab.dms.expiry.*}.
 *
 * @param jobEnabled   false: no reminders are sent (the lapse check Dispatch asks for still answers)
 * @param jobCron      when the reminder job runs (server time)
 * @param zone         the day a document lapses is counted in this zone
 * @param reminderDays days before expiry a reminder goes out (a lapsed notice follows on its own)
 * @param batchSize    documents read per page by the job
 * @param maxOwners    the most owners one lapse check may ask about
 * @param nms          where reminders are sent
 */
@Validated
@ConfigurationProperties(prefix = "railcab.dms.expiry")
public record DocumentExpiryProperties(
        @DefaultValue("true") boolean jobEnabled,
        @DefaultValue("0 30 7 * * *") @NotBlank String jobCron,
        @DefaultValue("Asia/Kolkata") @NotNull ZoneId zone,
        @DefaultValue({"30", "15", "7", "1"}) @NotEmpty List<@Min(1) @Max(365) Integer> reminderDays,
        @DefaultValue("200") @Min(1) @Max(1000) int batchSize,
        @DefaultValue("500") @Min(1) @Max(5000) int maxOwners,
        @DefaultValue @Valid Nms nms) {

    /**
     * @param baseUrl        NMS base URL (an Eureka name goes through the load balancer)
     * @param internalApiKey the {@code X-NMS-API-Key}; empty sends none
     * @param callTimeoutMs  timeout of one call
     * @param maxAttempts    attempts of one (idempotent) call
     */
    public record Nms(
            @DefaultValue("http://RealBoom-NMS") @NotBlank String baseUrl,
            @DefaultValue("") String internalApiKey,
            @DefaultValue("5000") @Min(100) int callTimeoutMs,
            @DefaultValue("3") @Min(1) @Max(5) int maxAttempts) {
    }
}
