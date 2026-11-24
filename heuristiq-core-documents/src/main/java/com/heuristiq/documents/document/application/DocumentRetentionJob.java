package com.heuristiq.documents.document.application;

import com.heuristiq.documents.common.lock.DistributedLock;
import com.heuristiq.documents.config.DmsProperties;
import com.heuristiq.documents.document.domain.DocumentMetadata;
import com.heuristiq.documents.document.persistence.DocumentMetadataStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * Runs on every replica, so the purge is guarded by a short-lived {@link DistributedLock} lease.
 * Without it, every pod in the deployment (HPA runs 3-12) would race to purge the same batch
 * on every fire of the cron, inflating DB load and audit/metric noise.
 */
@Component
public class DocumentRetentionJob {

    private static final Logger log = LoggerFactory.getLogger(DocumentRetentionJob.class);

    private final DmsProperties properties;
    private final DocumentMetadataStore repository;
    private final DistributedLock lock;
    private final DocumentAuditService auditService;
    private final String instanceId = UUID.randomUUID().toString();

    public DocumentRetentionJob(
            DmsProperties properties,
            DocumentMetadataStore repository,
            DistributedLock lock,
            DocumentAuditService auditService
    ) {
        this.properties = properties;
        this.repository = repository;
        this.lock = lock;
        this.auditService = auditService;
    }

    @Scheduled(cron = "${railcab.dms.lifecycle.purge-job-cron}")
    public void purgeDeletedMetadata() {
        if (!properties.getLifecycle().isPurgeJobEnabled()) {
            return;
        }
        long purgeAfterDays = properties.getLifecycle().getPurgeDeletedAfterDays();
        if (purgeAfterDays < 1) {
            log.warn("Skipping DMS purge job because purgeDeletedAfterDays={} is below 1", purgeAfterDays);
            return;
        }

        if (!lock.tryAcquire(DocumentConstants.RETENTION_LOCK_KEY, DocumentConstants.RETENTION_LOCK_LEASE, instanceId)) {
            log.info("event=dms_retention_purge status=skipped reason=lock_held_by_another_instance");
            return;
        }
        try {
            Instant cutoff = Instant.now().minus(purgeAfterDays, ChronoUnit.DAYS);
            List<DocumentMetadata> expired = repository.findByDeletedTrueAndDeletedAtBefore(cutoff);
            if (expired.isEmpty()) {
                log.info("event=dms_retention_purge status=skipped reason=no_expired_metadata cutoff={}", cutoff);
                return;
            }

            expired.forEach(document -> auditService.recordPurge(document, "Deleted document purged after retention"));
            repository.deleteAll(expired);
            log.info("event=dms_retention_purge status=success purgedCount={} cutoff={}", expired.size(), cutoff);
        } finally {
            lock.release(DocumentConstants.RETENTION_LOCK_KEY, instanceId);
        }
    }
}
