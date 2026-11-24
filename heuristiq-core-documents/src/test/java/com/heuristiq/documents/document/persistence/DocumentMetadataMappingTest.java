package com.heuristiq.documents.document.persistence;

import com.heuristiq.documents.document.domain.DocumentMetadata;
import com.heuristiq.documents.document.persistence.mongo.DocumentMetadataMongoDocument;
import com.heuristiq.documents.document.persistence.postgres.DocumentMetadataJpaEntity;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Both metadata stores round-trip the new encryption field, and a legacy document without it reads as null. */
class DocumentMetadataMappingTest {

    private static DocumentMetadata sample(String encryption) {
        return DocumentMetadata.builder().id("d1").documentType("PHOTO_ID").category("kyc").ownerId("u1").tenantId("org-a")
                .objectKey("org-a/kyc/general/u1/f.pdf").provider("s3").encryption(encryption).status("UPLOADED")
                .createdAt(Instant.parse("2026-03-01T00:00:00Z")).retentionUntil(Instant.parse("2027-03-01T00:00:00Z")).build();
    }

    @Test
    void mongoRoundTripKeepsEncryptionAndTenant() {
        assertEquals(sample("SSE_KMS"), DocumentMetadataMongoDocument.fromDomain(sample("SSE_KMS")).toDomain());
    }

    @Test
    void postgresRoundTripKeepsEncryptionAndTenant() {
        assertEquals(sample("SSE_S3"), DocumentMetadataJpaEntity.fromDomain(sample("SSE_S3")).toDomain());
    }

    @Test
    void documentTypeRoundTripsAndLegacyRowsReadAsNull() {
        assertEquals("PHOTO_ID", DocumentMetadataMongoDocument.fromDomain(sample("NONE")).toDomain().getDocumentType());
        assertEquals("PHOTO_ID", DocumentMetadataJpaEntity.fromDomain(sample("NONE")).toDomain().getDocumentType());
        DocumentMetadata legacy = DocumentMetadata.builder().id("old").tenantId("org-a").build();
        assertNull(DocumentMetadataMongoDocument.fromDomain(legacy).toDomain().getDocumentType());
        assertNull(DocumentMetadataJpaEntity.fromDomain(legacy).toDomain().getDocumentType());
    }

    @Test
    void legacyDocumentsWithoutEncryptionReadAsNull() {
        assertNull(DocumentMetadataMongoDocument.fromDomain(sample(null)).toDomain().getEncryption());
        assertNull(DocumentMetadataJpaEntity.fromDomain(sample(null)).toDomain().getEncryption());
        assertEquals("org-a", DocumentMetadataJpaEntity.fromDomain(sample(null)).toDomain().getTenantId());
    }
}
