package com.heuristiq.documents.documenttype.domain;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The verification rule of {@link DocumentTypeValidator}: not required means NONE, required means a real method. */
class DocumentTypeVerificationRuleTest {

    private static DocumentTypeFields fields(Boolean verificationRequired, String verificationMethod) {
        return new DocumentTypeFields("PHOTO_ID", "docs", null, Map.of("en", "Photo ID"), List.of("PDF"), 5, true, null,
                null, null, verificationRequired, verificationMethod, true);
    }

    private static DocumentTypeException invalid(DocumentTypeFields fields) {
        DocumentTypeException ex = assertThrows(DocumentTypeException.class, () -> DocumentTypeValidator.normalize(fields, true));
        assertEquals(DocumentTypeException.Kind.INVALID, ex.kind());
        return ex;
    }

    @Test
    void missingVerificationFieldsMeanNoVerification() {
        DocumentTypeDraft draft = DocumentTypeValidator.normalize(fields(null, null), true);

        assertFalse(draft.verificationRequired());
        assertEquals(VerificationMethod.NONE, draft.verificationMethod());
    }

    @Test
    void theOlderFieldConstructorsMeanNoVerification() {
        DocumentTypeDraft draft = DocumentTypeValidator.normalize(new DocumentTypeFields("PHOTO_ID", "docs", null,
                Map.of("en", "x"), List.of("PDF"), 5, true, null, 30, false, true), true);

        assertFalse(draft.verificationRequired());
        assertEquals(VerificationMethod.NONE, draft.verificationMethod());
        assertEquals(30, draft.retentionDays());
        assertFalse(draft.scanRequired());
    }

    @Test
    void notRequiredWithABlankOrExplicitNoneMethodIsNone() {
        assertEquals(VerificationMethod.NONE, DocumentTypeValidator.normalize(fields(false, " "), true).verificationMethod());
        assertEquals(VerificationMethod.NONE, DocumentTypeValidator.normalize(fields(false, "none"), true).verificationMethod());
        assertEquals(VerificationMethod.NONE, DocumentTypeValidator.normalize(fields(null, "NONE"), true).verificationMethod());
    }

    @Test
    void requiredWithAMethodIsAcceptedAndTheMethodIsNormalized() {
        DocumentTypeDraft draft = DocumentTypeValidator.normalize(fields(true, " aadhaar_digilocker "), true);

        assertTrue(draft.verificationRequired());
        assertEquals(VerificationMethod.AADHAAR_DIGILOCKER, draft.verificationMethod());
        for (VerificationMethod method : VerificationMethod.values()) {
            if (method != VerificationMethod.NONE) {
                assertEquals(method, DocumentTypeValidator.normalize(fields(true, method.name()), false).verificationMethod());
            }
        }
    }

    @Test
    void aMethodWithoutRequiredVerificationIsRejected() {
        assertTrue(invalid(fields(false, "MANUAL_REVIEW")).getMessage()
                .contains(DocumentTypeConstants.MSG_VERIFICATION_METHOD_WITHOUT_REQUIRED));
        assertTrue(invalid(fields(null, "PAN_VERIFICATION")).getMessage()
                .contains(DocumentTypeConstants.MSG_VERIFICATION_METHOD_WITHOUT_REQUIRED));
    }

    @Test
    void requiredVerificationWithoutAMethodIsRejected() {
        assertTrue(invalid(fields(true, null)).getMessage().contains(DocumentTypeConstants.MSG_VERIFICATION_METHOD_MISSING));
        assertTrue(invalid(fields(true, "")).getMessage().contains(DocumentTypeConstants.MSG_VERIFICATION_METHOD_MISSING));
        assertTrue(invalid(fields(true, "NONE")).getMessage().contains(DocumentTypeConstants.MSG_VERIFICATION_METHOD_MISSING));
    }

    @Test
    void anUnknownMethodIsRejectedByName() {
        String message = invalid(fields(true, "FACE_MATCH")).getMessage();

        assertTrue(message.contains("Unknown verificationMethod 'FACE_MATCH'"), message);
        assertFalse(message.contains(DocumentTypeConstants.MSG_VERIFICATION_METHOD_MISSING));
    }

    @Test
    void verificationProblemsAreReportedTogetherWithTheOtherProblems() {
        DocumentTypeFields bad = new DocumentTypeFields("ab", "docs", null, Map.of("en", "x"), List.of("PDF"), 5, true,
                null, null, null, false, "MANUAL_REVIEW", true);

        String message = invalid(bad).getMessage();

        assertTrue(message.contains("code must be"));
        assertTrue(message.contains(DocumentTypeConstants.MSG_VERIFICATION_METHOD_WITHOUT_REQUIRED));
    }

    @Test
    void storedMethodKeysReadBackAsNoneWhenMissingOrUnknown() {
        assertEquals(VerificationMethod.NONE, VerificationMethod.fromStored(null));
        assertEquals(VerificationMethod.NONE, VerificationMethod.fromStored(""));
        assertEquals(VerificationMethod.NONE, VerificationMethod.fromStored("RETIRED_METHOD"));
        assertEquals(VerificationMethod.BANK_PENNY_DROP, VerificationMethod.fromStored("bank_penny_drop"));
        assertTrue(VerificationMethod.fromKey(null).isEmpty());
    }

    @Test
    void aNullMethodOnTheRecordsReadsAsNone() {
        DocumentType type = new DocumentType("id", "t", "C_ODE", "docs", null, Map.of("en", "x"),
                java.util.EnumSet.of(DocumentFormat.PDF), 5, false, null, null, true, false, null, true, null, null, null,
                null, null, null, List.of());
        DocumentTypeRule rule = new DocumentTypeRule("C_ODE", "docs", 5, List.of(), List.of(), List.of(), null, true, false, null);

        assertEquals(VerificationMethod.NONE, type.verificationMethod());
        assertEquals(VerificationMethod.NONE, rule.verificationMethod());
        assertEquals(VerificationMethod.NONE, type.withVersion(3L).verificationMethod());
    }
}
