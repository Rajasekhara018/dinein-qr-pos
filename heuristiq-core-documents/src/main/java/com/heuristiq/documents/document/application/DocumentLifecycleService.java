package com.heuristiq.documents.document.application;

import com.heuristiq.documents.config.DmsProperties;
import com.heuristiq.documents.document.domain.DocumentMetadata;
import com.heuristiq.documents.exception.DmsException;
import com.heuristiq.documents.security.DmsSecurityContext;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
public class DocumentLifecycleService {

    private final DmsProperties properties;

    private final Clock clock;

    public DocumentLifecycleService(DmsProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    /** Applies the service-wide default retention only (an upload that names no document type). */
    public void applyDefaults(DocumentMetadata metadata) {
        applyDefaults(metadata, null);
    }

    /**
     * Sets {@code retentionUntil} to now plus the retention of the document type, or, when the type sets none (or no
     * type was named), plus {@code railcab.dms.lifecycle.default-retention-days}; nothing is set when both are absent.
     *
     * @param typeRetentionDays the document type's retention in days, or null
     */
    public void applyDefaults(DocumentMetadata metadata, Integer typeRetentionDays) {
        long retentionDays = typeRetentionDays != null
                ? typeRetentionDays : properties.getLifecycle().getDefaultRetentionDays();
        if (retentionDays > 0) {
            metadata.setRetentionUntil(clock.instant().plus(retentionDays, ChronoUnit.DAYS));
        }
    }

    public void assertCanDelete(DocumentMetadata metadata) {
        if (metadata.isLegalHold()) {
            throw DmsException.forbidden("Document is under legal hold");
        }
        Instant retentionUntil = metadata.getRetentionUntil();
        if (retentionUntil != null && retentionUntil.isAfter(clock.instant())) {
            throw DmsException.forbidden("Document retention period has not expired");
        }
    }

    public void applyLegalHold(DocumentMetadata metadata, boolean enabled) {
        metadata.setLegalHold(enabled);
        metadata.setLegalHoldChangedAt(clock.instant());
        metadata.setLegalHoldChangedBy(DmsSecurityContext.currentPrincipal().userId());
    }
}
