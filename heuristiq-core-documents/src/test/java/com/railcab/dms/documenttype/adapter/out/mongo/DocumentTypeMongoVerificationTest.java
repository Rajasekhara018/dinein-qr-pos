package com.railcab.dms.documenttype.adapter.out.mongo;

import com.railcab.dms.documenttype.application.DefaultDocumentTypeService;
import com.railcab.dms.documenttype.domain.DocumentFormat;
import com.railcab.dms.documenttype.domain.DocumentType;
import com.railcab.dms.documenttype.domain.PrebuiltDocumentTypes;
import com.railcab.dms.documenttype.domain.VerificationMethod;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Verification fields in the Mongo document, old documents without them, and seeding the catalog through this adapter. */
class DocumentTypeMongoVerificationTest {

    private static final Instant NOW = Instant.parse("2026-11-09T10:00:00Z");

    private static DocumentType verified() {
        return new DocumentType("id-1", "org-a", "DRIVER_BANK_PROOF", "driver-documents", "DRIVER_ONBOARDING",
                new LinkedHashMap<>(Map.of("en", "Bank")), EnumSet.of(DocumentFormat.PDF), 5, true, null, 2920, true, true,
                VerificationMethod.BANK_PENNY_DROP, true, 1L, NOW, NOW, "system", "system", null, List.of(), "CASHFREE");
    }

    @Test
    void verificationRoundTrips() {
        DocumentTypeMongoDocument doc = DocumentTypeMongoDocument.fromDomain(verified());

        assertEquals(Boolean.TRUE, doc.verificationRequired);
        assertEquals("BANK_PENNY_DROP", doc.verificationMethod);
        assertEquals("CASHFREE", doc.verificationProvider);
        assertEquals(verified(), doc.toDomain());
    }

    @Test
    void anOldDocumentWithoutTheFieldsReadsAsFalseAndNone() {
        DocumentTypeMongoDocument doc = DocumentTypeMongoDocument.fromDomain(verified());
        doc.verificationRequired = null;
        doc.verificationMethod = null;

        DocumentType back = doc.toDomain();

        assertFalse(back.verificationRequired());
        assertEquals(VerificationMethod.NONE, back.verificationMethod());
    }

    @Test
    void anUnknownStoredMethodReadsAsNoneAndTheProbeLeavesTheFieldsUnset() {
        DocumentTypeMongoDocument doc = DocumentTypeMongoDocument.fromDomain(verified());
        doc.verificationMethod = "SOMETHING_OLD";

        assertEquals(VerificationMethod.NONE, doc.toDomain().verificationMethod());
        DocumentTypeMongoDocument probe = DocumentTypeMongoDocument.probe("org-a", null, null, true);
        assertNull(probe.verificationRequired);
        assertNull(probe.verificationMethod);
    }

    @Test
    void seedingThroughMongoCreatesMissingCodesAndNeverWritesAnExistingOne() {
        SpringDataDocumentTypeRepository spring = mock(SpringDataDocumentTypeRepository.class);
        DocumentType existing = new DocumentType("own", "org-a", "FRANCHISE_AGREEMENT", "mine", null, Map.of("en", "Ours"),
                EnumSet.of(DocumentFormat.PDF), 2, false, null, null, true, false, 7L, NOW, NOW, "m", "m", "cr", List.of("cr"));
        when(spring.findByTenantIdAndCode(eq("org-a"), anyString())).thenReturn(Optional.empty());
        when(spring.findByTenantIdAndCode("org-a", "FRANCHISE_AGREEMENT"))
                .thenReturn(Optional.of(DocumentTypeMongoDocument.fromDomain(existing)));
        when(spring.save(any())).thenAnswer(inv -> inv.getArgument(0));
        MongoDocumentTypeRepository adapter = new MongoDocumentTypeRepository(spring);

        new DefaultDocumentTypeService(adapter, Clock.fixed(NOW, ZoneOffset.UTC),
                new DefaultDocumentTypeService.Settings(true, 10, null, true)).ensureFor("org-a");

        ArgumentCaptor<DocumentTypeMongoDocument> saved = ArgumentCaptor.forClass(DocumentTypeMongoDocument.class);
        verify(spring, atLeastOnce()).save(saved.capture());
        List<String> codes = saved.getAllValues().stream().map(d -> d.code).toList();
        assertEquals(PrebuiltDocumentTypes.entries().size(), codes.size(), "DEFAULT plus every catalog code except the existing one");
        assertFalse(codes.contains("FRANCHISE_AGREEMENT"));
        assertTrue(codes.contains("DEFAULT"));
        saved.getAllValues().forEach(d -> assertEquals("org-a", d.tenantId));
        DocumentTypeMongoDocument licence = saved.getAllValues().stream().filter(d -> "DRIVING_LICENCE".equals(d.code))
                .findFirst().orElseThrow();
        assertEquals("DRIVING_LICENCE_SARATHI", licence.verificationMethod);
        assertEquals(20, licence.expiryYears);
    }
}
