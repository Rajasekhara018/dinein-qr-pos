package com.railcab.dms.documenttype.adapter.out.mongo;

import com.railcab.dms.documenttype.domain.DocumentFormat;
import com.railcab.dms.documenttype.domain.DocumentType;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Mapping and error translation of the Mongo adapter (no MongoDB is available to the unit tests). */
class DocumentTypeMongoDocumentTest {

    static DocumentType sample() {
        return new DocumentType("id-1", "org-a", "PHOTO_ID", "docs", null, new LinkedHashMap<>(Map.of("en", "Photo")),
                EnumSet.of(DocumentFormat.PDF, DocumentFormat.JPG), 5, true, null, 365, false, false, 3L, Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-02T00:00:00Z"), "m1", "m2", "cr-2", List.of("cr-1", "cr-2"));
    }

    @Test
    void roundTripKeepsEveryField() {
        DocumentType back = DocumentTypeMongoDocument.fromDomain(sample()).toDomain();
        assertEquals(sample(), back);
        assertNull(back.flow());
        assertNull(back.expiryYears());
    }

    @Test
    void probeCarriesOnlyOrganizationAndFilters() {
        DocumentTypeMongoDocument probe = DocumentTypeMongoDocument.probe("org-a", "docs", null, true);
        assertEquals("org-a", probe.tenantId);
        assertNull(probe.code);
        assertNull(probe.maxFileSizeMb, "unset boxed fields are ignored by query-by-example");
        assertNull(probe.required);
        assertNull(probe.version);
    }

    @Test
    void declaresTheCollectionAndTheThreeIndexes() {
        assertEquals("dms_document_types", DocumentTypeMongoDocument.class.getAnnotation(Document.class).collection());
        var indexes = DocumentTypeMongoDocument.class.getAnnotation(CompoundIndexes.class).value();
        assertEquals(3, indexes.length);
        assertTrue(indexes[0].unique());
        assertTrue(indexes[0].def().contains("tenantId") && indexes[0].def().contains("code"));
    }

    @Test
    void saveTranslatesLockAndDuplicateFailuresToConflicts() {
        SpringDataDocumentTypeRepository spring = mock(SpringDataDocumentTypeRepository.class);
        MongoDocumentTypeRepository adapter = new MongoDocumentTypeRepository(spring);

        org.mockito.Mockito.doThrow(new OptimisticLockingFailureException("stale")).when(spring).save(any());
        assertEquals(com.railcab.dms.documenttype.domain.DocumentTypeException.Kind.CONFLICT,
                assertThrows(com.railcab.dms.documenttype.domain.DocumentTypeException.class, () -> adapter.save(sample())).kind());

        org.mockito.Mockito.doThrow(new DuplicateKeyException("dup")).when(spring).save(any());
        assertEquals(com.railcab.dms.documenttype.domain.DocumentTypeException.Kind.CONFLICT,
                assertThrows(com.railcab.dms.documenttype.domain.DocumentTypeException.class, () -> adapter.save(sample())).kind());
    }

    @Test
    void lookupsAlwaysCarryTheOrganization() {
        SpringDataDocumentTypeRepository spring = mock(SpringDataDocumentTypeRepository.class);
        MongoDocumentTypeRepository adapter = new MongoDocumentTypeRepository(spring);
        when(spring.findByTenantIdAndId("org-a", "id-1")).thenReturn(java.util.Optional.of(DocumentTypeMongoDocument.fromDomain(sample())));

        assertTrue(adapter.findById("org-a", "id-1").isPresent());
        assertTrue(adapter.findById("org-b", "id-1").isEmpty());
        assertTrue(adapter.findByCode("org-b", "PHOTO_ID").isEmpty());
    }

    @Test
    void legacyRecordWithoutRetentionAndScanFieldsDefaultsToServiceDefaultAndScanRequired() {
        DocumentTypeMongoDocument doc = DocumentTypeMongoDocument.fromDomain(sample());
        doc.retentionDays = null;
        doc.scanRequired = null;

        DocumentType back = doc.toDomain();

        assertNull(back.retentionDays());
        assertEquals(true, back.scanRequired());
    }

    @Test
    void retentionAndScanSettingsRoundTrip() {
        DocumentType back = DocumentTypeMongoDocument.fromDomain(sample()).toDomain();

        assertEquals(sample().retentionDays(), back.retentionDays());
        assertEquals(sample().scanRequired(), back.scanRequired());
        assertEquals(false, back.scanRequired());
    }
}
