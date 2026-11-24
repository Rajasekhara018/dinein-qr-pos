package com.railcab.dms.document.application;

import com.railcab.dms.common.lock.DistributedLock;
import com.railcab.dms.config.DmsProperties;
import com.railcab.dms.document.domain.DocumentMetadata;
import com.railcab.dms.document.persistence.DocumentMetadataStore;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DocumentRetentionJobTest {

    private final DmsProperties properties = new DmsProperties();
    private final DocumentMetadataStore store = mock(DocumentMetadataStore.class);
    private final DistributedLock lock = mock(DistributedLock.class);
    private final DocumentAuditService audit = mock(DocumentAuditService.class);
    private final DocumentRetentionJob job = new DocumentRetentionJob(properties, store, lock, audit);

    @Test
    void everyPurgedDocumentGetsAPurgeEventBeforeItsMetadataIsDeleted() {
        DocumentMetadata a = DocumentMetadata.builder().id("a").tenantId("org-a").build();
        DocumentMetadata b = DocumentMetadata.builder().id("b").tenantId("org-b").build();
        when(lock.tryAcquire(anyString(), any(), anyString())).thenReturn(true);
        when(store.findByDeletedTrueAndDeletedAtBefore(any())).thenReturn(List.of(a, b));

        job.purgeDeletedMetadata();

        verify(audit).recordPurge(a, "Deleted document purged after retention");
        verify(audit).recordPurge(b, "Deleted document purged after retention");
        verify(store).deleteAll(List.of(a, b));
    }

    @Test
    void nothingIsRecordedWhenTheLockIsHeldElsewhere() {
        when(lock.tryAcquire(anyString(), any(), anyString())).thenReturn(false);

        job.purgeDeletedMetadata();

        verify(audit, never()).recordPurge(any(), anyString());
        verify(store, never()).deleteAll(any());
    }
}
