package com.heuristiq.audit.adapter.in.scheduler;

import com.heuristiq.audit.application.AuditRetentionService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduler adapter: triggers {@link AuditRetentionService#purge()} on the configured interval
 * ({@code railcab.audit.retention.purge-interval-ms}, first run one interval after start). Switch off with
 * {@code railcab.audit.retention.purge-enabled=false}.
 */
@Component
@ConditionalOnProperty(prefix = "railcab.audit.retention", name = "purge-enabled", havingValue = "true", matchIfMissing = true)
public class AuditRetentionJob {

    private final AuditRetentionService retention;

    /**
     * Creates the job.
     *
     * @param retention the purge use case
     */
    public AuditRetentionJob(AuditRetentionService retention) {
        this.retention = retention;
    }

    /** Runs one purge round. */
    @Scheduled(initialDelayString = "${railcab.audit.retention.purge-interval-ms:3600000}",
            fixedDelayString = "${railcab.audit.retention.purge-interval-ms:3600000}")
    public void run() {
        retention.purge();
    }
}
