package com.heuristiq.documents.expiry.adapter.in;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.heuristiq.documents.common.lock.DistributedLock;
import com.heuristiq.documents.expiry.application.DocumentExpiryService;
import com.heuristiq.documents.expiry.application.ExpiryConstants;
import com.heuristiq.documents.expiry.config.DocumentExpiryProperties;

/**
 * Sends the day's document expiry reminders (D12). Every replica fires the cron, so a {@link DistributedLock} lease lets only one
 * of them run; a failure is logged and the next run catches up (a missed reminder day sends the next smaller reminder, never twice).
 */
@Component
public class DocumentExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(DocumentExpiryJob.class);

    private final DocumentExpiryService expiry;
    private final DocumentExpiryProperties properties;
    private final DistributedLock lock;
    private final String instanceId = UUID.randomUUID().toString();

    public DocumentExpiryJob(DocumentExpiryService expiry, DocumentExpiryProperties properties, DistributedLock lock) {
        this.expiry = expiry;
        this.properties = properties;
        this.lock = lock;
    }

    @Scheduled(cron = "${railcab.dms.expiry.job-cron:0 30 7 * * *}")
    public void sendReminders() {
        if (!properties.jobEnabled()) {
            return;
        }
        if (!lock.tryAcquire(ExpiryConstants.REMINDER_LOCK_KEY, ExpiryConstants.REMINDER_LOCK_LEASE, instanceId)) {
            log.info("event=dms_expiry_reminders status=skipped reason=lock_held_by_another_instance");
            return;
        }
        try {
            int sent = expiry.sendDueReminders();
            log.info("event=dms_expiry_reminders status=success sent={}", sent);
        } catch (RuntimeException e) {
            // a scheduler boundary: logged with context; the next run retries what was not sent
            log.error("event=dms_expiry_reminders status=failed reason={}", e.getMessage(), e);
        } finally {
            lock.release(ExpiryConstants.REMINDER_LOCK_KEY, instanceId);
        }
    }
}
