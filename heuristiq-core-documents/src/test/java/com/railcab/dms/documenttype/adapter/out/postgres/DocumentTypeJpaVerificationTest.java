package com.railcab.dms.documenttype.adapter.out.postgres;

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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Verification fields in the Postgres row, old rows without them, and seeding the catalog through this adapter. */
class DocumentTypeJpaVerificationTest {

    private static final Instant NOW = Instant.parse("2026-11-09T10:00:00Z");

    private static DocumentType verified() {
        return new DocumentType("id-1", "org-a", "VEHICLE_RC", "driver-documents", "DRIVER_ONBOARDING",
                new LinkedHashMap<>(Map.of("en", "RC")), EnumSet.of(DocumentFormat.PDF), 5, true, 15, 2920, true, true,
                VerificationMethod.VEHICLE_RC_VAHAN, true, 1L, NOW, NOW, "system", "system", null, List.of(), "KARZA");
    }

    @Test
    void verificationRoundTrips() {
        DocumentTypeJpaEntity entity = DocumentTypeJpaEntity.fromDomain(verified());

        assertEquals(Boolean.TRUE, entity.verificationRequired);
        assertEquals("VEHICLE_RC_VAHAN", entity.verificationMethod);
        assertEquals("KARZA", entity.verificationProvider);
        assertEquals(verified(), entity.toDomain());
    }

    @Test
    void anOldRowWithoutTheColumnsReadsAsFalseAndNone() {
        DocumentTypeJpaEntity entity = DocumentTypeJpaEntity.fromDomain(verified());
        entity.verificationRequired = null;
        entity.verificationMethod = null;

        DocumentType back = entity.toDomain();

        assertFalse(back.verificationRequired());
        assertEquals(VerificationMethod.NONE, back.verificationMethod());
    }

    @Test
    void anUnknownStoredMethodReadsAsNone() {
        DocumentTypeJpaEntity entity = DocumentTypeJpaEntity.fromDomain(verified());
        entity.verificationMethod = "RETIRED_PROVIDER";

        assertEquals(VerificationMethod.NONE, entity.toDomain().verificationMethod());
    }

    @Test
    void theProbeLeavesTheVerificationColumnsUnset() {
        DocumentTypeJpaEntity probe = DocumentTypeJpaEntity.probe("org-a", null, null, null);

        assertEquals(null, probe.verificationRequired);
        assertEquals(null, probe.verificationMethod);
    }

    @Test
    void seedingThroughPostgresCreatesMissingCodesAndNeverWritesAnExistingOne() {
        DocumentTypeJpaRepository spring = mock(DocumentTypeJpaRepository.class);
        DocumentType existing = new DocumentType("own", "org-a", "DRIVER_PAN", "mine", null, Map.of("en", "Ours"),
                EnumSet.of(DocumentFormat.PDF), 2, false, null, null, true, false, 7L, NOW, NOW, "m", "m", "cr", List.of("cr"));
        when(spring.findByTenantIdAndCode(eq("org-a"), anyString())).thenReturn(Optional.empty());
        when(spring.findByTenantIdAndCode("org-a", "DRIVER_PAN")).thenReturn(Optional.of(DocumentTypeJpaEntity.fromDomain(existing)));
        when(spring.save(any())).thenAnswer(inv -> inv.getArgument(0));
        PostgresDocumentTypeRepository adapter = new PostgresDocumentTypeRepository(spring);

        new DefaultDocumentTypeService(adapter, Clock.fixed(NOW, ZoneOffset.UTC),
                new DefaultDocumentTypeService.Settings(true, 10, null, true)).ensureFor("org-a");

        ArgumentCaptor<DocumentTypeJpaEntity> saved = ArgumentCaptor.forClass(DocumentTypeJpaEntity.class);
        verify(spring, atLeastOnce()).save(saved.capture());
        List<String> codes = saved.getAllValues().stream().map(e -> e.code).toList();
        assertEquals(PrebuiltDocumentTypes.entries().size(), codes.size(), "DEFAULT plus every catalog code except DRIVER_PAN");
        assertFalse(codes.contains("DRIVER_PAN"));
        assertTrue(codes.contains("DEFAULT"));
        DocumentTypeJpaEntity aadhaar = saved.getAllValues().stream().filter(e -> "DRIVER_AADHAAR".equals(e.code))
                .findFirst().orElseThrow();
        assertEquals("org-a", aadhaar.tenantId);
        assertEquals(Boolean.TRUE, aadhaar.verificationRequired);
        assertEquals("AADHAAR_DIGILOCKER", aadhaar.verificationMethod);
        saved.getAllValues().stream().filter(e -> "CUSTOMER_PHOTO".equals(e.code)).findFirst()
                .ifPresentOrElse(e -> assertEquals("NONE", e.verificationMethod), () -> { throw new AssertionError(); });
    }
}
