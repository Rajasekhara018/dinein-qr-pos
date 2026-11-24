package com.railcab.dms.documenttype.adapter.out.postgres;

import com.railcab.dms.documenttype.domain.DocumentFormat;
import com.railcab.dms.documenttype.domain.DocumentType;
import com.railcab.dms.documenttype.domain.DocumentTypeException;
import jakarta.persistence.Table;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;

import java.time.Instant;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Mapping and error translation of the Postgres adapter (no PostgreSQL is available to the unit tests). */
class DocumentTypeJpaEntityTest {

    private static DocumentType sample() {
        return new DocumentType("id-1", "org-a", "PHOTO_ID", "docs", "FLOW_X",
                new LinkedHashMap<>(Map.of("en", "Photo, ID", "hi", "फोटो")), EnumSet.of(DocumentFormat.PDF, DocumentFormat.JPG),
                5, false, 7, 90, false, true, 3L, Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-02T00:00:00Z"), "m1", "m2",
                "cr-2", List.of("cr-1", "cr-2"));
    }

    @Test
    void roundTripKeepsEveryFieldIncludingNonAsciiNamesWithCommas() {
        assertEquals(sample(), DocumentTypeJpaEntity.fromDomain(sample()).toDomain());
    }

    @Test
    void emptyCollectionsAndNullsSurvive() {
        DocumentTypeJpaEntity entity = DocumentTypeJpaEntity.fromDomain(sample());
        entity.recentChangeRequestIds = "";
        entity.names = null;
        DocumentType back = entity.toDomain();
        assertTrue(back.recentChangeRequestIds().isEmpty());
        assertTrue(back.names().isEmpty());
        assertNull(DocumentTypeJpaEntity.probe("t", null, null, null).version);
    }

    @Test
    void tableHasTheUniqueCodePerOrganization() {
        Table table = DocumentTypeJpaEntity.class.getAnnotation(Table.class);
        assertEquals("dms_document_types", table.name());
        assertEquals(List.of("tenantId", "code"), List.of(table.uniqueConstraints()[0].columnNames()));
        assertEquals(2, table.indexes().length);
    }

    @Test
    void saveTranslatesLockAndDuplicateFailuresToConflicts() {
        DocumentTypeJpaRepository spring = mock(DocumentTypeJpaRepository.class);
        PostgresDocumentTypeRepository adapter = new PostgresDocumentTypeRepository(spring);
        org.mockito.Mockito.doThrow(new OptimisticLockingFailureException("stale")).when(spring).save(any());
        assertEquals(DocumentTypeException.Kind.CONFLICT,
                assertThrows(DocumentTypeException.class, () -> adapter.save(sample())).kind());
        org.mockito.Mockito.doThrow(new DataIntegrityViolationException("dup")).when(spring).save(any());
        assertEquals(DocumentTypeException.Kind.CONFLICT,
                assertThrows(DocumentTypeException.class, () -> adapter.save(sample())).kind());
    }

    @Test
    void lookupsAlwaysCarryTheOrganization() {
        DocumentTypeJpaRepository spring = mock(DocumentTypeJpaRepository.class);
        PostgresDocumentTypeRepository adapter = new PostgresDocumentTypeRepository(spring);
        when(spring.findByTenantIdAndCode("org-a", "PHOTO_ID")).thenReturn(Optional.of(DocumentTypeJpaEntity.fromDomain(sample())));
        assertTrue(adapter.findByCode("org-a", "PHOTO_ID").isPresent());
        assertTrue(adapter.findByCode("org-b", "PHOTO_ID").isEmpty());
    }

    @Test
    void legacyRecordWithoutRetentionAndScanFieldsDefaultsToServiceDefaultAndScanRequired() {
        DocumentTypeJpaEntity entity = DocumentTypeJpaEntity.fromDomain(sample());
        entity.retentionDays = null;
        entity.scanRequired = null;

        DocumentType back = entity.toDomain();

        assertNull(back.retentionDays());
        assertEquals(true, back.scanRequired());
    }

    @Test
    void retentionAndScanSettingsRoundTrip() {
        DocumentType back = DocumentTypeJpaEntity.fromDomain(sample()).toDomain();

        assertEquals(sample().retentionDays(), back.retentionDays());
        assertEquals(sample().scanRequired(), back.scanRequired());
        assertEquals(false, back.scanRequired());
    }
}
