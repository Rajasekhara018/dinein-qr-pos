package com.railcab.dms.documenttype.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.railcab.dms.documenttype.application.DocumentTypeSnapshots;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DocumentTypeProviderTest {
    private static final Instant NOW = Instant.parse("2026-11-17T00:00:00Z");

    private DocumentTypeFields fields(String method, String provider) {
        return new DocumentTypeFields("DRIVER_BANK_PROOF", "driver-documents", "DRIVER_ONBOARDING", Map.of("en", "Bank proof"),
                List.of("PDF"), 5, true, null, 2920, true, true, method, true, provider);
    }

    @Test
    void storesOneNormalizedProviderAndRejectsUnsupportedPairs() {
        var draft = DocumentTypeValidator.normalize(fields("BANK_PENNY_DROP", " cashfree "), true);
        assertEquals("CASHFREE", draft.verificationProvider());
        assertThrows(DocumentTypeException.class, () -> DocumentTypeValidator.normalize(fields("BANK_PENNY_DROP", "IDFY"), true));
        assertThrows(DocumentTypeException.class, () -> DocumentTypeValidator.normalize(fields("MANUAL_REVIEW", "CASHFREE"), true));
        assertThrows(DocumentTypeException.class, () -> DocumentTypeValidator.normalize(fields("BANK_PENNY_DROP", "UNKNOWN"), true));
    }

    @Test
    void approvedSnapshotCarriesTheScalarProviderAndProviderChangesAlterTheHash() {
        var draft = DocumentTypeValidator.normalize(fields("BANK_PENNY_DROP", "CASHFREE"), true);
        var after = DocumentTypeSnapshots.proposedOf("id", "org-a", "DRIVER_BANK_PROOF", draft, "maker", "UPDATE", 1L);
        assertEquals("CASHFREE", after.get("verificationProvider"));
        assertFalse(after.containsKey("verificationProviders"));
        var roundTrip = DocumentTypeSnapshots.fieldsOf(new ObjectMapper().valueToTree(after));
        assertEquals("CASHFREE", roundTrip.verificationProvider());
    }

    @Test
    void seedsExactlyOneDefaultProviderAndEveryChoiceSupportsTheMethod() {
        for (var entry : PrebuiltDocumentTypes.entries()) {
            var type = PrebuiltDocumentTypes.create(entry, "org-a", entry.code(), NOW, 10);
            if (type.verificationProvider() != null) {
                assertTrue(VerificationProviderCatalog.parse(type.verificationProvider()).supports(type.verificationMethod()));
            } else {
                assertTrue(type.verificationMethod() == VerificationMethod.NONE || type.verificationMethod() == VerificationMethod.MANUAL_REVIEW);
            }
        }
        assertEquals("SIGNZY", VerificationProviderCatalog.defaultProvider(VerificationMethod.AADHAAR_DIGILOCKER));
        assertEquals("CASHFREE", VerificationProviderCatalog.defaultProvider(VerificationMethod.BANK_PENNY_DROP));
    }

    @Test
    void upgradesUntouchedOldSeedsButPreservesAdminChoices() {
        var entry = PrebuiltDocumentTypes.entries().stream().filter(e -> e.code().equals("DRIVER_PAN")).findFirst().orElseThrow();
        var original = new DocumentType("id", "org-a", entry.code(), entry.category(), entry.flow(), entry.names(), entry.formats(),
                5, false, null, 2920, true, true, entry.verificationMethod(), true, 1L, NOW, NOW, "SYSTEM", "SYSTEM", null, List.of());
        // Use the actual seeder's actor value rather than assuming its case.
        original = new DocumentType(original.id(), original.tenantId(), original.code(), original.category(), original.flow(), original.names(),
                original.allowedFormats(), original.maxFileSizeMb(), original.required(), original.expiryYears(), original.retentionDays(),
                original.scanRequired(), original.verificationRequired(), original.verificationMethod(), original.active(), original.version(),
                NOW, NOW, DocumentTypeConstants.SYSTEM_ACTOR, DocumentTypeConstants.SYSTEM_ACTOR, null, List.of());
        var upgraded = PrebuiltDocumentTypes.withProviderDefaults(entry, original, NOW).orElseThrow();
        assertEquals("KARZA", upgraded.verificationProvider());
        assertTrue(upgraded.required());
        assertTrue(PrebuiltDocumentTypes.withProviderDefaults(entry, upgraded, NOW).isEmpty());
        var custom = new DocumentType(upgraded.id(), upgraded.tenantId(), upgraded.code(), upgraded.category(), upgraded.flow(), upgraded.names(),
                upgraded.allowedFormats(), 5, false, null, 2920, true, true, entry.verificationMethod(), true, 2L,
                NOW, NOW, DocumentTypeConstants.SYSTEM_ACTOR, "admin", "approved-change", List.of("approved-change"), "SIGNZY");
        assertTrue(PrebuiltDocumentTypes.withProviderDefaults(entry, custom, NOW).isEmpty());
    }
}
