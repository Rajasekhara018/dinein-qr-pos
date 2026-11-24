package com.heuristiq.uam.readiness.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Settings of the go-live guard, override, alerts and scheduled re-check ({@code railcab.uam.readiness.governance.*}).
 *
 * @param guardEnabled        false switches the go-live guard off (an emergency switch; the guard is ON by default)
 * @param overrideReasonMin   shortest accepted override reason
 * @param overrideReasonMax   longest accepted override reason (also the storage limit)
 * @param ownerTeamMax        most people in one owner team
 * @param alertInterval       the same blocking set is not alerted again within this time
 * @param alertsEnabled       false stops all alerts (the guard and the re-check still run)
 * @param maxAlertRecipients  most people one alert is sent to (super admins plus the owner team)
 * @param recheckEnabled      false switches the scheduled re-check off (ON by default)
 * @param recheckInterval     time between re-checks
 * @param recheckInitialDelay wait after start before the first re-check
 * @param recheckConcurrency  organization-application checks evaluated at the same time
 * @param recheckMaxLinks     most links one run evaluates (the rest wait for the next run)
 * @param leaseTtl            how long one instance holds the re-check lease
 */
@Validated
@ConfigurationProperties(prefix = "railcab.uam.readiness.governance")
public record GovernanceProperties(
        @DefaultValue("true") boolean guardEnabled,
        @DefaultValue("10") @Min(1) int overrideReasonMin,
        @DefaultValue("500") @Min(1) @Max(500) int overrideReasonMax,
        @DefaultValue("10") @Min(1) @Max(100) int ownerTeamMax,
        @DefaultValue("24h") @NotNull Duration alertInterval,
        @DefaultValue("true") boolean alertsEnabled,
        @DefaultValue("50") @Min(1) @Max(500) int maxAlertRecipients,
        @DefaultValue("true") boolean recheckEnabled,
        @DefaultValue("6h") @NotNull Duration recheckInterval,
        @DefaultValue("5m") @NotNull Duration recheckInitialDelay,
        @DefaultValue("2") @Min(1) @Max(16) int recheckConcurrency,
        @DefaultValue("500") @Min(1) int recheckMaxLinks,
        @DefaultValue("30m") @NotNull Duration leaseTtl) {
}
