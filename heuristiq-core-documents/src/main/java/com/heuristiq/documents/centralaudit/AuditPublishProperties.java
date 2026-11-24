package com.railcab.dms.centralaudit;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Push of DMS audit events to the central audit service ({@code railcab.dms.audit.publish.*}).
 *
 * @param enabled                    queue and push events; when false nothing is queued
 * @param baseUrl                    explicit audit service address; when blank the Eureka name in {@code discoveryUrl} is used
 * @param discoveryUrl               Eureka-resolved address of Rider_AuditService
 * @param serviceToken               shared credential sent as X-Rider-Service-Token (RIDER_INTERNAL_SERVICE_TOKEN);
 *                                   events stay queued while it is blank
 * @param batchSize                  events per push
 * @param intervalMs                 delay between drain runs
 * @param leaseSeconds               how long one instance owns a queued event while sending it
 * @param maxAttempts                tries before an event is marked DEAD
 * @param retryInitialBackoffSeconds first retry delay, doubling up to the maximum
 * @param retryMaxBackoffSeconds     longest retry delay
 * @param outboxRetentionDays        queued events older than this are dropped whether or not they were sent
 * @param callTimeoutMs              timeout of one push
 * @param callMaxAttempts            attempts of one push inside the resilient client
 */
@Validated
@ConfigurationProperties(prefix = "railcab.dms.audit.publish")
public record AuditPublishProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("") String baseUrl,
        @DefaultValue("http://RealBoom-AuditService") String discoveryUrl,
        @DefaultValue("") String serviceToken,
        @Min(1) @DefaultValue("100") int batchSize,
        @Min(100) @DefaultValue("5000") long intervalMs,
        @Min(1) @DefaultValue("60") int leaseSeconds,
        @Min(1) @DefaultValue("20") int maxAttempts,
        @Min(1) @DefaultValue("15") int retryInitialBackoffSeconds,
        @Min(1) @DefaultValue("900") int retryMaxBackoffSeconds,
        @Min(1) @DefaultValue("7") int outboxRetentionDays,
        @Min(100) @DefaultValue("3000") long callTimeoutMs,
        @Min(1) @DefaultValue("3") int callMaxAttempts
) {
}
