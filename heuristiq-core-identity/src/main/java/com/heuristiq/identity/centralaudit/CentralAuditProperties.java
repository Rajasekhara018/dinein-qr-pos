package com.heuristiq.identity.centralaudit;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Outbox and push of UAM domain audit events to the central audit service ({@code railcab.uam.audit.publish.*}).
 *
 * @param enabled            store and push events; when false events are dropped
 * @param baseUrl            address of Rider_AuditService
 * @param serviceToken       shared credential sent as X-Rider-Service-Token (RIDER_INTERNAL_SERVICE_TOKEN); nothing is
 *                           sent while it is blank
 * @param maxRows            most outbox rows kept; above it the oldest rows older than the retention are dropped (logged at ERROR)
 * @param retentionHours     age after which an undelivered row may be dropped when the outbox is over {@code maxRows}
 * @param batchSize          events per push
 * @param intervalMs         delay between drain runs
 * @param callTimeoutMs      timeout of one push
 * @param maxAttempts        attempts of one push inside the resilient client (the ingest is idempotent on eventId)
 * @param maxConcurrentCalls bulkhead size
 * @param leaseMs            how long a claimed batch is reserved for one instance before another may take it
 * @param backoffInitialMs   delay before the first retry of a failed row; doubles on each further failure
 * @param backoffMaxMs       longest delay between retries of one row
 */
@Validated
@ConfigurationProperties(prefix = "railcab.uam.audit.publish")
public record CentralAuditProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("http://localhost:9106") String baseUrl,
        @DefaultValue("") String serviceToken,
        @Min(1) @DefaultValue("100000") int maxRows,
        @Min(1) @DefaultValue("168") int retentionHours,
        @Min(1) @DefaultValue("100") int batchSize,
        @Min(100) @DefaultValue("2000") long intervalMs,
        @Min(100) @DefaultValue("3000") long callTimeoutMs,
        @Min(1) @DefaultValue("2") int maxAttempts,
        @Min(1) @DefaultValue("5") int maxConcurrentCalls,
        @Min(1000) @DefaultValue("60000") long leaseMs,
        @Min(100) @DefaultValue("2000") long backoffInitialMs,
        @Min(100) @DefaultValue("300000") long backoffMaxMs
) {
}
