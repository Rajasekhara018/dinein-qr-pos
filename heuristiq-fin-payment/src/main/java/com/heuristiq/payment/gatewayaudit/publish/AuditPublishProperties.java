package com.heuristiq.payment.gatewayaudit.publish;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/** {@code rider.audit.publish.*}: delivery of payment-gateway audit events to Rider_AuditService. */
@Component
@ConfigurationProperties(prefix = "rider.audit.publish")
@Validated
@Getter
@Setter
public class AuditPublishProperties {

    /** Master switch; when false events are still stored (and delivered once re-enabled). */
    private boolean enabled = true;

    /** Base URL of the audit service (Eureka name REALBOOM-AUDITSERVICE), no path. */
    @NotBlank
    private String url = "http://localhost:9106";

    /** Events per ingest request; the audit service accepts at most 500. */
    @Min(1)
    @Max(500)
    private int batchSize = 100;

    /** Delay between drain runs. */
    @Min(250)
    private long intervalMs = 5000;

    /** Batches sent per drain run before yielding. */
    @Min(1)
    @Max(100)
    private int maxBatchesPerRun = 10;

    /** Longest wait before a failed batch is retried. */
    @Min(1)
    private long maxBackoffSeconds = 300;

    /** Delivered rows are deleted after this many days. */
    @Min(1)
    private int publishedRetentionDays = 7;

    /** Delay between purges of delivered rows. */
    @Min(60000)
    private long purgeIntervalMs = 3_600_000L;
}
