package com.heuristiq.documents.documenttype.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Content of the prebuilt catalog: exactly the contract's table, valid under the normal rules, names in all 7 languages. */
class PrebuiltDocumentTypesTest {

    private static final Instant NOW = Instant.parse("2026-11-09T10:00:00Z");
    private static final Set<DocumentFormat> IMG = EnumSet.of(DocumentFormat.JPG, DocumentFormat.PNG, DocumentFormat.WEBP);
    private static final Set<DocumentFormat> DOC = EnumSet.of(DocumentFormat.PDF, DocumentFormat.JPG, DocumentFormat.PNG);
    private static final Set<DocumentFormat> FRANCHISE_PHOTO =
            EnumSet.of(DocumentFormat.PDF, DocumentFormat.JPG, DocumentFormat.PNG, DocumentFormat.WEBP);

    /** One expected row of the contract table. */
    private record Row(String code, String category, String flow, Set<DocumentFormat> formats, int maxMb, boolean required,
                       Integer expiryYears, Integer retentionDays, boolean verificationRequired, VerificationMethod method) {
    }

    private static final List<Row> TABLE = List.of(
            new Row("CUSTOMER_PHOTO", "customer-documents", "CUSTOMER_ONBOARDING", IMG, 5, false, null, null, false, VerificationMethod.NONE),
            new Row("CUSTOMER_ID_PROOF", "customer-documents", "CUSTOMER_ONBOARDING", DOC, 5, false, null, 2920, true, VerificationMethod.MANUAL_REVIEW),
            new Row("CUSTOMER_ADDRESS_PROOF", "customer-documents", "CUSTOMER_ONBOARDING", DOC, 5, false, null, 2920, true, VerificationMethod.MANUAL_REVIEW),
            new Row("DRIVER_AADHAAR", "driver-documents", "DRIVER_ONBOARDING", DOC, 5, true, null, 2920, true, VerificationMethod.AADHAAR_DIGILOCKER),
            new Row("DRIVER_PAN", "driver-documents", "DRIVER_ONBOARDING", DOC, 5, true, null, 2920, true, VerificationMethod.PAN_VERIFICATION),
            new Row("DRIVING_LICENCE", "driver-documents", "DRIVER_ONBOARDING", DOC, 5, true, 20, 2920, true, VerificationMethod.DRIVING_LICENCE_SARATHI),
            new Row("VEHICLE_RC", "driver-documents", "DRIVER_ONBOARDING", DOC, 5, true, 15, 2920, true, VerificationMethod.VEHICLE_RC_VAHAN),
            new Row("VEHICLE_INSURANCE", "driver-documents", "DRIVER_ONBOARDING", DOC, 5, true, 1, 2920, true, VerificationMethod.MANUAL_REVIEW),
            new Row("VEHICLE_PUC", "driver-documents", "DRIVER_ONBOARDING", DOC, 5, true, null, 2920, true, VerificationMethod.MANUAL_REVIEW),
            new Row("VEHICLE_PERMIT", "driver-documents", "DRIVER_ONBOARDING", DOC, 5, false, null, 2920, true, VerificationMethod.MANUAL_REVIEW),
            new Row("DRIVER_PHOTO", "driver-documents", "DRIVER_ONBOARDING", IMG, 5, true, null, null, false, VerificationMethod.NONE),
            new Row("DRIVER_BANK_PROOF", "driver-documents", "DRIVER_ONBOARDING", DOC, 5, true, null, 2920, true, VerificationMethod.BANK_PENNY_DROP),
            new Row("DRIVER_POLICE_VERIFICATION", "driver-documents", "DRIVER_ONBOARDING", DOC, 5, false, 1, 2920, true, VerificationMethod.MANUAL_REVIEW),
            new Row("FRANCHISE_ID_PROOF", "franchise-onboarding", "FRANCHISE_ONBOARDING", DOC, 5, true, null, 2920, true, VerificationMethod.MANUAL_REVIEW),
            new Row("FRANCHISE_ADDRESS_PROOF", "franchise-onboarding", "FRANCHISE_ONBOARDING", DOC, 5, true, null, 2920, true, VerificationMethod.MANUAL_REVIEW),
            new Row("FRANCHISE_BUSINESS_REGISTRATION", "franchise-onboarding", "FRANCHISE_ONBOARDING", DOC, 10, false, null, 2920, true, VerificationMethod.MANUAL_REVIEW),
            new Row("FRANCHISE_TAX_CERTIFICATE", "franchise-onboarding", "FRANCHISE_ONBOARDING", DOC, 10, false, null, 2920, true, VerificationMethod.MANUAL_REVIEW),
            new Row("FRANCHISE_BANK_PROOF", "franchise-onboarding", "FRANCHISE_ONBOARDING", DOC, 5, true, null, 2920, true, VerificationMethod.BANK_PENNY_DROP),
            new Row("FRANCHISE_AGREEMENT", "franchise-onboarding", "FRANCHISE_ONBOARDING", DOC, 20, true, null, 3650, false, VerificationMethod.NONE),
            new Row("FRANCHISE_PHOTO", "franchise-onboarding", "FRANCHISE_ONBOARDING", FRANCHISE_PHOTO, 5, false, null, null, false, VerificationMethod.NONE));

    @Test
    void theCatalogIsExactlyTheContractTableInOrder() {
        List<PrebuiltDocumentTypes.Entry> entries = PrebuiltDocumentTypes.entries();

        assertEquals(TABLE.size(), entries.size());
        for (int i = 0; i < TABLE.size(); i++) {
            Row row = TABLE.get(i);
            PrebuiltDocumentTypes.Entry entry = entries.get(i);
            assertEquals(row.code(), entry.code());
            assertEquals(row.category(), entry.category(), row.code());
            assertEquals(row.flow(), entry.flow(), row.code());
            assertEquals(row.formats(), entry.formats(), row.code());
            assertEquals(row.maxMb(), entry.maxFileSizeMb(), row.code());
            assertEquals(row.required(), entry.required(), row.code());
            assertEquals(row.expiryYears(), entry.expiryYears(), row.code());
            assertEquals(row.retentionDays(), entry.retentionDays(), row.code());
            assertEquals(row.verificationRequired(), entry.verificationRequired(), row.code());
            assertEquals(row.method(), entry.verificationMethod(), row.code());
        }
    }

    @Test
    void codesAreUniqueAndNeverTheDefaultCode() {
        Set<String> codes = new HashSet<>();
        for (PrebuiltDocumentTypes.Entry entry : PrebuiltDocumentTypes.entries()) {
            assertTrue(codes.add(entry.code()), "duplicate " + entry.code());
            assertFalse(DefaultDocumentTypes.isDefaultCode(entry.code()));
        }
    }

    @Test
    void everyTypeHasANameInAllSevenLanguagesAndNoEnglishCopiedIntoOtherLanguages() {
        for (PrebuiltDocumentTypes.Entry entry : PrebuiltDocumentTypes.entries()) {
            Map<String, String> names = entry.names();
            assertEquals(new HashSet<>(DocumentTypeConstants.LANGUAGES), names.keySet(), entry.code());
            String english = names.get("en");
            for (String language : DocumentTypeConstants.LANGUAGES) {
                String name = names.get(language);
                assertFalse(name == null || name.isBlank(), entry.code() + " " + language);
                assertTrue(name.length() <= DocumentTypeConstants.MAX_NAME_LENGTH, entry.code() + " " + language);
                if (!"en".equals(language)) {
                    assertNotEquals(english, name, entry.code() + " " + language + " is the English text");
                }
            }
        }
    }

    @Test
    void everyEntryPassesTheSameValidationAsAnAdminProposal() {
        for (PrebuiltDocumentTypes.Entry entry : PrebuiltDocumentTypes.entries()) {
            DocumentTypeDraft draft = DocumentTypeValidator.normalize(new DocumentTypeFields(entry.code(), entry.category(),
                    entry.flow(), entry.names(), entry.formats().stream().map(Enum::name).toList(), entry.maxFileSizeMb(),
                    entry.required(), entry.expiryYears(), entry.retentionDays(), true, entry.verificationRequired(),
                    entry.verificationMethod().name(), true), true);
            assertEquals(entry.code(), draft.code());
            assertEquals(entry.category(), draft.category());
            assertEquals(entry.verificationMethod(), draft.verificationMethod());
        }
    }

    @Test
    void createBuildsASystemTypeWithScanAndActive() {
        PrebuiltDocumentTypes.Entry licence = entry("DRIVING_LICENCE");

        DocumentType type = PrebuiltDocumentTypes.create(licence, "org-a", "id-1", NOW, 50);

        assertEquals("id-1", type.id());
        assertEquals("org-a", type.tenantId());
        assertEquals("DRIVING_LICENCE", type.code());
        assertEquals("driver-documents", type.category());
        assertEquals("DRIVER_ONBOARDING", type.flow());
        assertEquals(5, type.maxFileSizeMb());
        assertEquals(20, type.expiryYears());
        assertEquals(2920, type.retentionDays());
        assertTrue(type.scanRequired());
        assertTrue(type.active());
        assertTrue(type.required());
        assertTrue(type.verificationRequired());
        assertEquals(VerificationMethod.DRIVING_LICENCE_SARATHI, type.verificationMethod());
        assertEquals(DocumentTypeConstants.SYSTEM_ACTOR, type.createdBy());
        assertEquals(DocumentTypeConstants.SYSTEM_ACTOR, type.updatedBy());
        assertNull(type.version());
        assertNull(type.lastChangeRequestId());
        assertTrue(type.recentChangeRequestIds().isEmpty());
        assertEquals(NOW, type.createdAt());
    }

    @Test
    void sizesAreCappedByTheOrganizationsConfiguredMaximum() {
        PrebuiltDocumentTypes.Entry agreement = entry("FRANCHISE_AGREEMENT");
        PrebuiltDocumentTypes.Entry photo = entry("CUSTOMER_PHOTO");

        assertEquals(20, PrebuiltDocumentTypes.create(agreement, "o", "i", NOW, 50).maxFileSizeMb());
        assertEquals(10, PrebuiltDocumentTypes.create(agreement, "o", "i", NOW, 10).maxFileSizeMb());
        assertEquals(3, PrebuiltDocumentTypes.create(photo, "o", "i", NOW, 3).maxFileSizeMb());
        assertEquals(5, PrebuiltDocumentTypes.create(photo, "o", "i", NOW, 10).maxFileSizeMb());
        assertEquals(DocumentTypeConstants.MIN_FILE_SIZE_MB, PrebuiltDocumentTypes.create(photo, "o", "i", NOW, 0).maxFileSizeMb());
        for (PrebuiltDocumentTypes.Entry entry : PrebuiltDocumentTypes.entries()) {
            assertTrue(PrebuiltDocumentTypes.create(entry, "o", "i", NOW, 10).maxFileSizeMb() <= 10, entry.code());
        }
    }

    @Test
    void createdFormatsAreACopyTheCatalogCannotBeChangedThrough() {
        DocumentType type = PrebuiltDocumentTypes.create(entry("CUSTOMER_PHOTO"), "o", "i", NOW, 10);
        type.allowedFormats().add(DocumentFormat.PDF);

        assertEquals(IMG, entry("CUSTOMER_PHOTO").formats());
    }

    private static PrebuiltDocumentTypes.Entry entry(String code) {
        return PrebuiltDocumentTypes.entries().stream().filter(e -> e.code().equals(code)).findFirst().orElseThrow();
    }
}
