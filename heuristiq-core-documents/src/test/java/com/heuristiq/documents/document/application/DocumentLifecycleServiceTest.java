package com.heuristiq.documents.document.application;

import com.heuristiq.documents.config.DmsProperties;
import com.heuristiq.documents.document.domain.DocumentMetadata;
import com.heuristiq.documents.exception.DmsException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DocumentLifecycleServiceTest {

    private final DmsProperties properties = new DmsProperties();
    private final DocumentLifecycleService service = new DocumentLifecycleService(properties, Clock.systemUTC());

    @Test
    void appliesDefaultRetention() {
        properties.getLifecycle().setDefaultRetentionDays(7);
        DocumentMetadata metadata = new DocumentMetadata();

        service.applyDefaults(metadata);

        assertNotNull(metadata.getRetentionUntil());
    }

    @Test
    void blocksDeleteDuringRetention() {
        DocumentMetadata metadata = new DocumentMetadata();
        metadata.setRetentionUntil(Instant.now().plusSeconds(60));

        assertThrows(DmsException.class, () -> service.assertCanDelete(metadata));
    }

    @Test
    void blocksDeleteUnderLegalHold() {
        DocumentMetadata metadata = new DocumentMetadata();
        metadata.setLegalHold(true);

        assertThrows(DmsException.class, () -> service.assertCanDelete(metadata));
    }
}
